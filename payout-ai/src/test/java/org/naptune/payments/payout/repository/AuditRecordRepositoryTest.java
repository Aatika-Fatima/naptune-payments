package org.naptune.payments.payout.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import jakarta.persistence.EntityManager;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.naptune.payments.payout.TestcontainersConfiguration;
import org.naptune.payments.payout.domain.PaymentStatus;
import org.naptune.payments.payout.entity.AuditRecord;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

// Build step 6 "Done when": the complete V2 applies, AuditRecord validates
// against its table, findByPaymentIdOrderById returns rows in insertion order,
// and getDetailReason() reads detail.reason.
//
// The data is the guide's sample: agent-a's proposal creates the scenario
// payment and claims its first evaluation, then an operator re-claims it after
// the block. Each test flushes and clears the persistence context so reads come
// from Postgres, not the first-level cache, then rolls back.
@SpringBootTest
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
@Transactional
class AuditRecordRepositoryTest {

    @Autowired
    AuditRecordRepository audits;
    @Autowired
    SupplierPaymentRepository payments;
    @Autowired
    EntityManager entityManager;

    // Use case: agent-a's proposal inserts the payment. The RECEIVED row has no
    // from_state, because nothing existed before, and keeps the spelling this
    // caller typed. recorded_at comes from the column default.
    @Test
    void receivedRowStartsTheHistoryWithTheCallersSpelling() {
        UUID paymentId = insertPayment("OBL-1");

        Long id = audits.save(AuditRecord.received(paymentId, "agent:agent-a", "INV-94018")).getId();
        reloadFromDatabase();

        AuditRecord row = audits.findById(id).orElseThrow();
        assertThat(row.getPaymentId()).isEqualTo(paymentId);
        assertThat(row.getActor()).isEqualTo("agent:agent-a");
        assertThat(row.getAction()).isEqualTo("RECEIVED");
        assertThat(row.getFromState()).isNull();
        assertThat(row.getToState()).isEqualTo("PROPOSED");
        assertThat(row.getDetailReason()).isEqualTo("received");
        assertThat(row.getDetail()).containsEntry("rawReference", "INV-94018");
        assertThat(row.getRecordedAt()).isNotNull();
    }

    // Use case: the state path reads straight down the two state columns, in id
    // order, across the first evaluation and the operator's re-claim:
    // ∅ → PROPOSED → VALIDATING → BLOCKED → VALIDATING.
    @Test
    void historyIsReturnedInInsertionOrder() {
        UUID paymentId = insertPayment("OBL-1");

        audits.save(AuditRecord.received(paymentId, "agent:agent-a", "INV-94018"));
        audits.save(AuditRecord.transition(paymentId, "agent:agent-a",
                PaymentStatus.PROPOSED, PaymentStatus.VALIDATING, "evaluation claimed"));
        // Stands in for the DECISION row until decision(…) arrives in step 7.
        audits.save(AuditRecord.transition(paymentId, "agent:agent-a",
                PaymentStatus.VALIDATING, PaymentStatus.BLOCKED, "BENEFICIARY_CHANGE_PENDING"));
        audits.save(AuditRecord.transition(paymentId, "ops-reviewer",
                PaymentStatus.BLOCKED, PaymentStatus.VALIDATING, "evaluation claimed"));
        reloadFromDatabase();

        List<AuditRecord> history = audits.findByPaymentIdOrderById(paymentId);
        assertThat(history)
                .extracting(AuditRecord::getActor, AuditRecord::getFromState, AuditRecord::getToState,
                        AuditRecord::getDetailReason)
                .containsExactly(
                        tuple("agent:agent-a", null, "PROPOSED", "received"),
                        tuple("agent:agent-a", "PROPOSED", "VALIDATING", "evaluation claimed"),
                        tuple("agent:agent-a", "VALIDATING", "BLOCKED", "BENEFICIARY_CHANGE_PENDING"),
                        tuple("ops-reviewer", "BLOCKED", "VALIDATING", "evaluation claimed"));
        assertThat(history).extracting(AuditRecord::getId).isSorted();
    }

    // Use case: INV-94018 is paid in two instalments, each its own payment. Rows
    // written in between belong to the other payment and leave gaps in the ids;
    // one payment's history must not include them.
    @Test
    void historyIsScopedToItsPayment() {
        UUID firstInstalment = insertPayment("OBL-1");
        UUID secondInstalment = insertPayment("OBL-2");

        audits.save(AuditRecord.received(firstInstalment, "agent:agent-a", "INV-94018"));
        audits.save(AuditRecord.received(secondInstalment, "payout-worker", "INV94018"));
        audits.save(AuditRecord.transition(firstInstalment, "agent:agent-a",
                PaymentStatus.PROPOSED, PaymentStatus.VALIDATING, "evaluation claimed"));
        reloadFromDatabase();

        assertThat(audits.findByPaymentIdOrderById(firstInstalment))
                .extracting(AuditRecord::getAction)
                .containsExactly("RECEIVED", "TRANSITION");
        assertThat(audits.findByPaymentIdOrderById(secondInstalment))
                .singleElement()
                .satisfies(row -> assertThat(row.getDetail()).containsEntry("rawReference", "INV94018"));
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
