package org.naptune.payments.payout.repository;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.persistence.EntityManager;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.naptune.payments.payout.TestcontainersConfiguration;
import org.naptune.payments.payout.entity.PaymentDecisionRecord;
import org.naptune.payments.payout.policy.Decision;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

// Build step 5 "Done when": PaymentDecisionRecord validates against its table,
// and with two rows for one payment findFirstByPaymentIdOrderByIdDesc returns
// the second while countByPaymentId returns 2.
//
// The data is the guide's sample: the scenario payment is blocked at 09:30 with
// the beneficiary change pending, the compliance API down and the duplicate
// consumer behind, then blocked again at 10:30 once compliance answers. Each test
// flushes and clears the persistence context so reads come from Postgres, not
// the first-level cache, then rolls back.
@SpringBootTest
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
@Transactional
class PaymentDecisionRepositoryTest {

    private static final String POLICY_VERSION = "payout-policy-1";
    private static final Duration DECISION_TTL = Duration.ofMinutes(5);
    private static final Instant FIRST_EVALUATION = Instant.parse("2026-10-22T09:30:01.204Z");
    private static final Instant RE_EVALUATION = Instant.parse("2026-10-22T10:30:44.512Z");

    @Autowired
    PaymentDecisionRepository decisions;
    @Autowired
    SupplierPaymentRepository payments;
    @Autowired
    EntityManager entityManager;

    // Use case: the first evaluation blocks on the pending beneficiary change and
    // records every reason, in the engine's order, with the six evidence ids it
    // saw. The JSONB lists round-trip and decided_at comes from the column default.
    @Test
    void firstEvaluationStoresEveryReasonInOrderWithItsEvidence() {
        UUID paymentId = insertPayment("OBL-1");
        List<UUID> evidenceIds = sixEvidenceIds();

        Long id = decisions.save(firstDecision(paymentId, evidenceIds)).getId();
        reloadFromDatabase();

        PaymentDecisionRecord row = decisions.findById(id).orElseThrow();
        assertThat(row.getPaymentId()).isEqualTo(paymentId);
        assertThat(row.getDecision()).isEqualTo(Decision.BLOCK);
        assertThat(row.getReasonCodes()).containsExactly(
                "BENEFICIARY_CHANGE_PENDING", "COMPLIANCE_EVIDENCE_UNAVAILABLE", "DUPLICATE_CHECK_STALE");
        assertThat(row.getEvidenceIds()).containsExactlyElementsOf(evidenceIds);
        assertThat(row.getPolicyVersion()).isEqualTo(POLICY_VERSION);
        assertThat(row.getValidUntil()).isEqualTo(FIRST_EVALUATION.plus(DECISION_TTL));
        assertThat(row.getDecidedAt()).isNotNull();
    }

    // Use case: an hour later compliance answers that XXXX9931 was never screened.
    // The re-evaluation adds a row; the first stays so the 09:30 decision can
    // still be explained, and the newest row is the current decision.
    @Test
    void reEvaluationAppendsAndTheLatestRowIsCurrent() {
        UUID paymentId = insertPayment("OBL-1");

        PaymentDecisionRecord first = decisions.save(firstDecision(paymentId, sixEvidenceIds()));
        PaymentDecisionRecord second = decisions.save(new PaymentDecisionRecord(paymentId, Decision.BLOCK,
                List.of("BENEFICIARY_CHANGE_PENDING", "COMPLIANCE_NOT_SCREENED"), sixEvidenceIds(),
                POLICY_VERSION, RE_EVALUATION.plus(DECISION_TTL)));
        reloadFromDatabase();

        assertThat(decisions.countByPaymentId(paymentId)).isEqualTo(2);
        PaymentDecisionRecord current = decisions.findFirstByPaymentIdOrderByIdDesc(paymentId).orElseThrow();
        assertThat(current.getId()).isEqualTo(second.getId()).isGreaterThan(first.getId());
        assertThat(current.getReasonCodes()).containsExactly("BENEFICIARY_CHANGE_PENDING", "COMPLIANCE_NOT_SCREENED");
        assertThat(decisions.findById(first.getId()).orElseThrow().getReasonCodes())
                .contains("COMPLIANCE_EVIDENCE_UNAVAILABLE");
    }

    // Use case: a dispatch-guard refusal (UC-04) also adds a row, with the guard's
    // reason code and no evidence. An empty JSONB list must round-trip as empty.
    @Test
    void refusalWithoutEvidenceStoresAnEmptyList() {
        UUID paymentId = insertPayment("OBL-1");

        Long id = decisions.save(new PaymentDecisionRecord(paymentId, Decision.DEFER,
                List.of("DECISION_EXPIRED"), List.of(), POLICY_VERSION, RE_EVALUATION)).getId();
        reloadFromDatabase();

        PaymentDecisionRecord row = decisions.findById(id).orElseThrow();
        assertThat(row.getEvidenceIds()).isEmpty();
        assertThat(row.getValidUntil()).isEqualTo(RE_EVALUATION);
    }

    // Use case: INV-94018 is paid in two instalments, each its own payment. The
    // current decision of one must never be read as the other's, or a block on
    // OBL-1 could hide behind an approval for OBL-2.
    @Test
    void currentDecisionIsScopedToItsPayment() {
        UUID firstInstalment = insertPayment("OBL-1");
        UUID secondInstalment = insertPayment("OBL-2");

        decisions.save(firstDecision(firstInstalment, sixEvidenceIds()));
        decisions.save(new PaymentDecisionRecord(secondInstalment, Decision.APPROVE, List.of(), sixEvidenceIds(),
                POLICY_VERSION, RE_EVALUATION.plus(DECISION_TTL)));
        reloadFromDatabase();

        assertThat(decisions.findFirstByPaymentIdOrderByIdDesc(firstInstalment).orElseThrow().getDecision())
                .isEqualTo(Decision.BLOCK);
        assertThat(decisions.countByPaymentId(firstInstalment)).isEqualTo(1);
        assertThat(decisions.findFirstByPaymentIdOrderByIdDesc(UUID.randomUUID())).isEmpty();
    }

    private PaymentDecisionRecord firstDecision(UUID paymentId, List<UUID> evidenceIds) {
        return new PaymentDecisionRecord(paymentId, Decision.BLOCK,
                List.of("BENEFICIARY_CHANGE_PENDING", "COMPLIANCE_EVIDENCE_UNAVAILABLE", "DUPLICATE_CHECK_STALE"),
                evidenceIds, POLICY_VERSION, FIRST_EVALUATION.plus(DECISION_TTL));
    }

    // evidence_ids is not a foreign key, so the ids need no evidence_record rows.
    private List<UUID> sixEvidenceIds() {
        return List.of(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());
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
