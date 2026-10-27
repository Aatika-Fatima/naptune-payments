package org.naptune.payments.payout.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.naptune.payments.payout.simulation.SimulatedWorld.APPROVED_ACCOUNT;
import static org.naptune.payments.payout.simulation.SimulatedWorld.INVOICE_AMOUNT_PAISE;
import static org.naptune.payments.payout.simulation.SimulatedWorld.PROPOSED_ACCOUNT;
import static org.naptune.payments.payout.simulation.SimulatedWorld.SUPPLIER;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.naptune.payments.payout.TestcontainersConfiguration;
import org.naptune.payments.payout.api.ProposalService.ProposalOutcome;
import org.naptune.payments.payout.domain.PaymentStatus;
import org.naptune.payments.payout.domain.exception.PayoutException.EconomicActionConflictException;
import org.naptune.payments.payout.domain.exception.PayoutException.EvaluationNotPossibleException;
import org.naptune.payments.payout.domain.exception.PayoutException.IdempotencyKeyReusedException;
import org.naptune.payments.payout.entity.AuditRecord;
import org.naptune.payments.payout.policy.Decision;
import org.naptune.payments.payout.repository.ApiIdempotencyRepository;
import org.naptune.payments.payout.repository.AuditRecordRepository;
import org.naptune.payments.payout.repository.EvidenceRecordRepository;
import org.naptune.payments.payout.repository.InvoiceAliasRepository;
import org.naptune.payments.payout.repository.PaymentDecisionRepository;
import org.naptune.payments.payout.repository.SupplierPaymentRepository;
import org.naptune.payments.payout.simulation.SimulatedWorld;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

// Build step 8 "Done when". Not @Transactional: the service's three
// transactions must really commit, so each test clears the tables itself,
// before and after, to leave nothing for the rolled-back repository tests.
@SpringBootTest
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class ProposalServiceTest {

    @Autowired
    ProposalService proposals;
    @Autowired
    SimulatedWorld world;
    @Autowired
    SupplierPaymentRepository payments;
    @Autowired
    InvoiceAliasRepository aliases;
    @Autowired
    ApiIdempotencyRepository idempotency;
    @Autowired
    EvidenceRecordRepository evidence;
    @Autowired
    PaymentDecisionRepository decisions;
    @Autowired
    AuditRecordRepository audit;

    @BeforeEach
    @AfterEach
    void clearTables() {
        audit.deleteAllInBatch();
        decisions.deleteAllInBatch();
        evidence.deleteAllInBatch();
        aliases.deleteAllInBatch();
        idempotency.deleteAllInBatch();
        payments.deleteAllInBatch();
        world.scenario();
    }

    @Test
    void scenarioProposalWritesOneOfEverythingAndThreeAuditRows() {
        ProposalOutcome outcome = proposals.propose("agent-a", "key-a", request("INV-94018", INVOICE_AMOUNT_PAISE));

        assertThat(outcome.created()).isTrue();
        assertThat(outcome.view().status()).isEqualTo(PaymentStatus.BLOCKED);
        assertThat(outcome.view().decision()).isEqualTo(Decision.BLOCK);
        assertThat(outcome.view().reasonCodes()).containsExactly(
                "BENEFICIARY_CHANGE_PENDING", "COMPLIANCE_EVIDENCE_UNAVAILABLE", "DUPLICATE_CHECK_STALE");
        assertThat(outcome.view().executionAllowed()).isFalse();
        assertCounts(1, 1, 1);
        assertThat(audit.findByPaymentIdOrderById(outcome.view().paymentId()))
                .extracting(AuditRecord::getDetailReason)
                .hasSize(3)
                .first().isEqualTo("received");
    }

    // The reference arrives raw; only its fingerprint meets vendor master's.
    @Test
    void allClearToTheApprovedAccountIsAuthorized() {
        world.allClear();

        ProposalOutcome outcome = proposals.propose("agent-a", "key-a",
                new ProposalRequest("LE-IN-01", SUPPLIER, "INV-94018", "OBL-1", "PO-94018",
                        INVOICE_AMOUNT_PAISE, "INR", " bank-a-xxxx7712"));

        assertThat(outcome.view().status()).isEqualTo(PaymentStatus.AUTHORIZED);
        assertThat(outcome.view().decision()).isEqualTo(Decision.APPROVE);
        assertThat(outcome.view().reasonCodes()).isEmpty();
        assertThat(outcome.view().executionAllowed()).isTrue();
    }

    // E-01: Agent A's INV-94018, Agent B's INV94018 and the scheduled worker,
    // all at once. One payment and one evaluation; every request is remembered.
    @Test
    void threeConcurrentProposalsMakeOnePaymentAndOneEvaluation() throws Exception {
        List<Callable<ProposalOutcome>> calls = List.of(
                () -> proposals.propose("agent-a", "key-a", request("INV-94018", INVOICE_AMOUNT_PAISE)),
                () -> proposals.propose("agent-b", "key-b", request("INV94018", INVOICE_AMOUNT_PAISE)),
                () -> proposals.propose("scheduler", "run-0930", request("INV-94018", INVOICE_AMOUNT_PAISE)));

        List<ProposalOutcome> outcomes = runTogether(calls);

        assertThat(outcomes).extracting(o -> o.view().paymentId()).containsOnly(outcomes.getFirst().view().paymentId());
        assertThat(outcomes).filteredOn(ProposalOutcome::created).hasSize(1);
        assertCounts(1, 3, 3);
    }

    @Test
    void retryWithTheSameKeyReturnsTheStoredPaymentAndWritesNothing() {
        ProposalOutcome first = proposals.propose("agent-a", "key-a", request("INV-94018", INVOICE_AMOUNT_PAISE));

        ProposalOutcome retry = proposals.propose("agent-a", "key-a", request("INV-94018", INVOICE_AMOUNT_PAISE));

        assertThat(retry.created()).isFalse();
        assertThat(retry.view()).isEqualTo(first.view());
        assertCounts(1, 1, 1);
    }

    @Test
    void sameKeyWithADifferentBodyIsRejected() {
        proposals.propose("agent-a", "key-a", request("INV-94018", INVOICE_AMOUNT_PAISE));

        assertThatThrownBy(() -> proposals.propose("agent-a", "key-a", request("INV94018", INVOICE_AMOUNT_PAISE)))
                .isInstanceOf(IdempotencyKeyReusedException.class);
        assertCounts(1, 1, 1);
    }

    @Test
    void sameEconomicActionWithDifferentTermsIsRejectedNotMerged() {
        UUID canonical = proposals.propose("agent-a", "key-a", request("INV-94018", INVOICE_AMOUNT_PAISE))
                .view().paymentId();

        assertThatThrownBy(() -> proposals.propose("agent-b", "key-b", request("INV94018", 48_57_000_00L)))
                .isInstanceOfSatisfying(EconomicActionConflictException.class,
                        e -> assertThat(e.getCanonicalPaymentId()).isEqualTo(canonical));
        // The refused request's alias and key rolled back with its transaction.
        assertCounts(1, 1, 1);
    }

    @Test
    void anotherObligationIsAGenuinelyDifferentPayment() {
        proposals.propose("agent-a", "key-a", request("INV-94018", INVOICE_AMOUNT_PAISE));

        ProposalOutcome second = proposals.propose("agent-a", "key-a2",
                new ProposalRequest("LE-IN-01", SUPPLIER, "INV-94018", "OBL-2", "PO-94018",
                        INVOICE_AMOUNT_PAISE, "INR", PROPOSED_ACCOUNT));

        assertThat(second.created()).isTrue();
        assertThat(payments.count()).isEqualTo(2);
        assertThat(decisions.count()).isEqualTo(2);
    }

    @Test
    void reevaluationOfABlockedPaymentAddsANewSnapshotAndKeepsTheOld() {
        UUID id = proposals.propose("agent-a", "key-a", request("INV-94018", INVOICE_AMOUNT_PAISE)).view().paymentId();

        PaymentView again = proposals.reevaluate(id, "ops");

        assertThat(again.decided()).isTrue();
        assertThat(evidence.findByPaymentId(id)).hasSize(12);
        assertThat(decisions.countByPaymentId(id)).isEqualTo(2);
    }

    @Test
    void retryNeverReopensARecordedDecision() {
        proposals.propose("agent-a", "key-a", request("INV-94018", INVOICE_AMOUNT_PAISE));

        proposals.propose("agent-b", "key-b", request("INV94018", INVOICE_AMOUNT_PAISE));

        assertThat(decisions.count()).isEqualTo(1);
    }

    @Test
    void unknownPaymentCannotBeReevaluated() {
        UUID unknown = UUID.randomUUID();

        assertThatThrownBy(() -> proposals.reevaluate(unknown, "ops"))
                .hasMessageContaining(unknown.toString());
    }

    @Test
    void reevaluationIsRefusedFromAStateThatCannotBeLeft() {
        world.allClear();
        UUID id = proposals.propose("agent-a", "key-a", new ProposalRequest("LE-IN-01", SUPPLIER, "INV-94018",
                "OBL-1", "PO-94018", INVOICE_AMOUNT_PAISE, "INR", APPROVED_ACCOUNT)).view().paymentId();

        assertThatThrownBy(() -> proposals.reevaluate(id, "ops"))
                .isInstanceOf(EvaluationNotPossibleException.class);
    }

    private void assertCounts(long paymentRows, long aliasRows, long idempotencyRows) {
        assertThat(payments.count()).as("payment").isEqualTo(paymentRows);
        assertThat(aliases.count()).as("invoice_alias").isEqualTo(aliasRows);
        assertThat(idempotency.count()).as("api_idempotency").isEqualTo(idempotencyRows);
        assertThat(evidence.count()).as("evidence_record").isEqualTo(6);
        assertThat(decisions.count()).as("payment_decision").isEqualTo(1);
        assertThat(audit.count()).as("audit_record").isEqualTo(3);
    }

    private static List<ProposalOutcome> runTogether(List<Callable<ProposalOutcome>> calls) throws Exception {
        CountDownLatch start = new CountDownLatch(1);
        try (ExecutorService pool = Executors.newFixedThreadPool(calls.size())) {
            List<Future<ProposalOutcome>> futures = calls.stream()
                    .map(call -> pool.submit(() -> {
                        start.await();
                        return call.call();
                    }))
                    .toList();
            start.countDown();
            List<ProposalOutcome> outcomes = new ArrayList<>();
            for (Future<ProposalOutcome> f : futures) {
                outcomes.add(f.get());
            }
            return outcomes;
        }
    }

    private static ProposalRequest request(String invoiceReference, long amountPaise) {
        return new ProposalRequest("LE-IN-01", SUPPLIER, invoiceReference, "OBL-1", "PO-94018",
                amountPaise, "INR", PROPOSED_ACCOUNT);
    }
}
