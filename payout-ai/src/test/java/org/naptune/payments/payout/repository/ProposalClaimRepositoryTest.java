package org.naptune.payments.payout.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.naptune.payments.payout.TestcontainersConfiguration;
import org.naptune.payments.payout.claims.ClaimSource;
import org.naptune.payments.payout.claims.ClaimSubject;
import org.naptune.payments.payout.claims.ClaimVerdict;
import org.naptune.payments.payout.domain.Origin;
import org.naptune.payments.payout.entity.PaymentDecisionRecord;
import org.naptune.payments.payout.entity.ProposalClaim;
import org.naptune.payments.payout.entity.SupplierPayment;
import org.naptune.payments.payout.policy.Decision;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

// Build step 10 "Done when": V2.1 applies after V2, and ProposalClaim and the
// two new payment columns validate against their tables.
//
// The data is the scenario agent's seven statements about INV-94018, judged
// against the decision that blocked it. Each test flushes and clears the
// persistence context so reads come from Postgres, then rolls back.
@SpringBootTest
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
@Transactional
class ProposalClaimRepositoryTest {

    private static final String EMAIL = "mail-7f3a";

    @Autowired
    ProposalClaimRepository claims;
    @Autowired
    PaymentDecisionRepository decisions;
    @Autowired
    SupplierPaymentRepository payments;
    @Autowired
    EntityManager entityManager;

    // Use case: the agent proposes with seven claims. Each is stored with its
    // subject, source and verdict, pointing at the decision that judged it, and
    // they read back in the order the agent made them.
    @Test
    void scenarioClaimsAreStoredInOrderAgainstTheirDecision() {
        UUID paymentId = insertPayment("OBL-1");
        Long decisionId = blockedDecision(paymentId);
        UUID turnId = UUID.randomUUID();

        claims.saveAll(List.of(
                claim(paymentId, turnId, decisionId, ClaimSubject.INVOICE, ClaimSource.AGENT_STATEMENT, null,
                        "The invoice is approved", ClaimVerdict.CONFIRMED),
                claim(paymentId, turnId, decisionId, ClaimSubject.BENEFICIARY_ACCOUNT, ClaimSource.AGENT_STATEMENT,
                        null, "The supplier is verified", ClaimVerdict.CONTRADICTED),
                claim(paymentId, turnId, decisionId, ClaimSubject.BENEFICIARY_ACCOUNT,
                        ClaimSource.RETRIEVED_DOCUMENT, EMAIL, "XXXX9931 is the latest account",
                        ClaimVerdict.CONTRADICTED),
                claim(paymentId, turnId, decisionId, ClaimSubject.DUPLICATE_PAYMENT, ClaimSource.AGENT_STATEMENT,
                        null, "This payment is not a duplicate", ClaimVerdict.UNVERIFIABLE),
                claim(paymentId, turnId, decisionId, ClaimSubject.COMPLIANCE_SCREENING, ClaimSource.AGENT_STATEMENT,
                        null, "Compliance is clear", ClaimVerdict.CONTRADICTED),
                claim(paymentId, turnId, decisionId, ClaimSubject.ROUTE, ClaimSource.AGENT_STATEMENT, null,
                        "Provider A is the best route", ClaimVerdict.NOT_ACCEPTED),
                claim(paymentId, turnId, decisionId, ClaimSubject.URGENCY, ClaimSource.AGENT_STATEMENT, null,
                        "Execute immediately", ClaimVerdict.NOT_ACCEPTED)));
        reloadFromDatabase();

        List<ProposalClaim> stored = claims.findByPaymentIdOrderById(paymentId);
        assertThat(stored)
                .extracting(ProposalClaim::getSubject, ProposalClaim::getVerdict)
                .containsExactly(
                        tuple(ClaimSubject.INVOICE, ClaimVerdict.CONFIRMED),
                        tuple(ClaimSubject.BENEFICIARY_ACCOUNT, ClaimVerdict.CONTRADICTED),
                        tuple(ClaimSubject.BENEFICIARY_ACCOUNT, ClaimVerdict.CONTRADICTED),
                        tuple(ClaimSubject.DUPLICATE_PAYMENT, ClaimVerdict.UNVERIFIABLE),
                        tuple(ClaimSubject.COMPLIANCE_SCREENING, ClaimVerdict.CONTRADICTED),
                        tuple(ClaimSubject.ROUTE, ClaimVerdict.NOT_ACCEPTED),
                        tuple(ClaimSubject.URGENCY, ClaimVerdict.NOT_ACCEPTED));
        assertThat(stored).allSatisfy(c -> {
            assertThat(c.getTurnId()).isEqualTo(turnId);
            assertThat(c.getDecisionId()).isEqualTo(decisionId);
            assertThat(c.getCreatedAt()).isNotNull();
        });
        assertThat(stored.get(2).getDocumentId()).isEqualTo(EMAIL);
    }

    // Use case: an operator pastes the supplier's email into the prompt. The
    // account number in it is masked before the statement is stored. The rule
    // knows no context: the IFSC's seven-digit run is masked too, and a
    // five-digit reference is left alone.
    @Test
    void accountNumbersInTheStatementAreMaskedToTheLastFour() {
        UUID paymentId = insertPayment("OBL-1");

        Long id = claims.save(claim(paymentId, UUID.randomUUID(), null, ClaimSubject.BENEFICIARY_ACCOUNT,
                ClaimSource.OPERATOR, null, "New account 501002349931, IFSC HDFC0001234, ref 12345",
                ClaimVerdict.CONTRADICTED)).getId();
        reloadFromDatabase();

        assertThat(claims.findById(id).orElseThrow().getStatement())
                .isEqualTo("New account XXXX9931, IFSC HDFCXXXX1234, ref 12345");
    }

    // Use case: a statement longer than the column is cut to 300 characters,
    // after masking, so it is stored rather than refused.
    @Test
    void aLongStatementIsCutTo300Characters() {
        UUID paymentId = insertPayment("OBL-1");

        Long id = claims.save(claim(paymentId, UUID.randomUUID(), null, ClaimSubject.URGENCY,
                ClaimSource.AGENT_STATEMENT, null, "Execute immediately. ".repeat(30),
                ClaimVerdict.NOT_ACCEPTED)).getId();
        reloadFromDatabase();

        assertThat(claims.findById(id).orElseThrow().getStatement()).hasSize(300);
    }

    // Use case: a claim made before any decision existed has no decision id.
    @Test
    void aClaimMayPrecedeAnyDecision() {
        UUID paymentId = insertPayment("OBL-1");

        Long id = claims.save(claim(paymentId, UUID.randomUUID(), null, ClaimSubject.INVOICE,
                ClaimSource.AGENT_STATEMENT, null, "The invoice is approved", ClaimVerdict.UNVERIFIABLE)).getId();
        reloadFromDatabase();

        assertThat(claims.findById(id).orElseThrow().getDecisionId()).isNull();
    }

    // ck_claim_document: a claim from a retrieved document must say which one.
    @Test
    void aRetrievedDocumentClaimWithoutADocumentIdIsRefused() {
        UUID paymentId = insertPayment("OBL-1");

        assertThatThrownBy(() -> {
            claims.save(claim(paymentId, UUID.randomUUID(), null, ClaimSubject.BENEFICIARY_ACCOUNT,
                    ClaimSource.RETRIEVED_DOCUMENT, null, "XXXX9931 is the latest account",
                    ClaimVerdict.CONTRADICTED));
            entityManager.flush();
        }).isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ck_claim_document");
    }

    // payment_id is a foreign key: a claim cannot be about a payment that does not exist.
    @Test
    void aClaimAboutAnUnknownPaymentIsRefused() {
        assertThatThrownBy(() -> {
            claims.save(claim(UUID.randomUUID(), UUID.randomUUID(), null, ClaimSubject.INVOICE,
                    ClaimSource.AGENT_STATEMENT, null, "The invoice is approved", ClaimVerdict.CONFIRMED));
            entityManager.flush();
        }).isInstanceOf(DataIntegrityViolationException.class);
    }

    // The two new payment columns. The intake insert does not name them yet (the
    // agent path arrives in step 12), so a new row reads the column defaults.
    @Test
    void aPaymentInsertedWithoutOriginReadsApiAndUnknown() {
        UUID paymentId = insertPayment("OBL-1");
        reloadFromDatabase();

        SupplierPayment payment = payments.findById(paymentId).orElseThrow();
        assertThat(payment.getProposedVia()).isEqualTo(Origin.API);
        assertThat(payment.getProposedBy()).isEqualTo("unknown");
    }

    private static ProposalClaim claim(UUID paymentId, UUID turnId, Long decisionId, ClaimSubject subject,
                                       ClaimSource source, String documentId, String statement,
                                       ClaimVerdict verdict) {
        return ProposalClaim.of(paymentId, turnId, decisionId, subject, source, documentId, statement, verdict);
    }

    private Long blockedDecision(UUID paymentId) {
        return decisions.save(new PaymentDecisionRecord(paymentId, Decision.BLOCK,
                List.of("BENEFICIARY_CHANGE_PENDING", "COMPLIANCE_EVIDENCE_UNAVAILABLE", "DUPLICATE_CHECK_STALE"),
                List.of(UUID.randomUUID()), "policy-v5", Instant.now().plusSeconds(300))).getId();
    }

    private void reloadFromDatabase() {
        entityManager.flush();
        entityManager.clear();
    }

    private UUID insertPayment(String obligationId) {
        UUID id = UUID.randomUUID();
        payments.insertIfAbsent(id, "LE-IN-01", "SUP-7781", "INV94018", obligationId,
                "PO-94018", 48_75_000_00L, "INR", "fp-1");
        return id;
    }
}
