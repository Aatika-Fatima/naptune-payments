package org.naptune.payments.payout.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.naptune.payments.payout.TestcontainersConfiguration;
import org.naptune.payments.payout.entity.EvidenceRecord;
import org.naptune.payments.payout.policy.Freshness;
import org.naptune.payments.payout.policy.PaymentCheckType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

// Build step 4 "Done when": V2 applies after V1, EvidenceRecord validates against
// its table, and the JSONB value round-trips a Map.
//
// The data is the brief's scenario: a ₹48,75,000 payment to SUP-7781 for
// INV-94018, evaluated while the beneficiary change to BANK-B / XXXX9931 is
// still pending, the live compliance API returns 503, and the duplicate
// consumer is 17 minutes behind. Each test flushes and clears the persistence
// context so reads come from Postgres, not the first-level cache, then rolls back.
@SpringBootTest
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
@Transactional
class EvidenceRecordRepositoryTest {

    private static final Instant EVALUATED_AT = Instant.parse("2026-10-22T09:30:00Z");

    @Autowired
    EvidenceRecordRepository evidence;
    @Autowired
    SupplierPaymentRepository payments;
    @Autowired
    EntityManager entityManager;

    // Use case: the first evaluation of the scenario payment asks all six
    // systems of record and stores one row per answer, whatever the answer was.
    // A later payment_decision cites these ids, so the decision can be replayed.
    @Test
    void scenarioEvaluationStoresOneRowPerPaymentCheckWithItsFreshness() {
        UUID paymentId = insertPayment("OBL-1");

        evidence.saveAll(scenarioSnapshot(paymentId));
        reloadFromDatabase();

        List<EvidenceRecord> stored = evidence.findByPaymentId(paymentId);
        assertThat(stored)
                .extracting(EvidenceRecord::getPaymentCheckType, EvidenceRecord::getSource, EvidenceRecord::getFreshness)
                .containsExactlyInAnyOrder(
                        tuple(PaymentCheckType.APPROVAL_AUTHORITY, "delegation-registry", Freshness.FRESH),
                        tuple(PaymentCheckType.INVOICE, "procurement", Freshness.FRESH),
                        tuple(PaymentCheckType.BENEFICIARY_ACCOUNT, "vendor-master", Freshness.FRESH),
                        tuple(PaymentCheckType.COMPLIANCE_SCREENING, "compliance-api", Freshness.UNAVAILABLE),
                        tuple(PaymentCheckType.DUPLICATE_PAYMENT, "duplicate-service", Freshness.STALE),
                        tuple(PaymentCheckType.AVAILABLE_FUNDS, "treasury-ledger", Freshness.FRESH));
        assertThat(stored).allSatisfy(row -> assertThat(row.getRecordedAt()).isNotNull());
    }

    // Use case: the vendor master says BANK-A / XXXX7712 is approved and a change
    // to BANK-B / XXXX9931 awaits second approval. The policy blocks with
    // BENEFICIARY_CHANGE_PENDING by reading both fingerprints out of the JSONB
    // value, and the dispatch guard later compares source_version to detect
    // that the vendor record changed after the decision.
    @Test
    void beneficiaryAnswerRoundTripsThroughJsonb() {
        UUID paymentId = insertPayment("OBL-1");
        UUID id = UUID.randomUUID();
        Instant observed = EVALUATED_AT.minusSeconds(5);

        evidence.save(new EvidenceRecord(id, paymentId, PaymentCheckType.BENEFICIARY_ACCOUNT, "vendor-master",
                Map.of("approvedFingerprint", "BANK-A-XXXX7712",
                        "pendingFingerprint", "BANK-B-XXXX9931",
                        "pendingApprovals", 1),
                "vm-v42", observed, Freshness.FRESH));
        reloadFromDatabase();

        EvidenceRecord row = evidence.findById(id).orElseThrow();
        assertThat(row.getPaymentId()).isEqualTo(paymentId);
        assertThat(row.getValue())
                .containsEntry("approvedFingerprint", "BANK-A-XXXX7712")
                .containsEntry("pendingFingerprint", "BANK-B-XXXX9931")
                .containsEntry("pendingApprovals", 1);
        assertThat(row.getSourceVersion()).isEqualTo("vm-v42");
        assertThat(row.getSourceTime()).isEqualTo(observed);
    }

    // Use case: the live compliance API returns HTTP 503. There is no record
    // version and no "true as of" time, but the failure is still evidence: it is
    // stored as UNAVAILABLE so the decision can show why it deferred with
    // COMPLIANCE_EVIDENCE_UNAVAILABLE. recorded_at comes from the column default.
    @Test
    void unavailableSourceIsStoredWithoutVersionOrSourceTime() {
        UUID paymentId = insertPayment("OBL-1");
        UUID id = UUID.randomUUID();

        evidence.save(new EvidenceRecord(id, paymentId, PaymentCheckType.COMPLIANCE_SCREENING, "compliance-api",
                Map.of("cause", "HTTP_503"), null, null, Freshness.UNAVAILABLE));
        reloadFromDatabase();

        EvidenceRecord row = evidence.findById(id).orElseThrow();
        assertThat(row.getFreshness()).isEqualTo(Freshness.UNAVAILABLE);
        assertThat(row.getValue()).containsEntry("cause", "HTTP_503");
        assertThat(row.getSourceVersion()).isNull();
        assertThat(row.getSourceTime()).isNull();
        assertThat(row.getRecordedAt()).isNotNull();
    }

    // Use case: an hour later the compliance API is back. Screening cleared the
    // supplier and XXXX7712 yesterday, but XXXX9931 has never been screened,
    // so the new answer is INCOMPLETE. Re-evaluation appends it; the 503 answer
    // stays so the first decision can still be explained.
    @Test
    void reEvaluationAppendsAndKeepsTheEarlierAnswer() {
        UUID paymentId = insertPayment("OBL-1");
        UUID firstAnswer = UUID.randomUUID();
        UUID secondAnswer = UUID.randomUUID();

        evidence.save(new EvidenceRecord(firstAnswer, paymentId, PaymentCheckType.COMPLIANCE_SCREENING, "compliance-api",
                Map.of("cause", "HTTP_503"), null, null, Freshness.UNAVAILABLE));
        evidence.save(new EvidenceRecord(secondAnswer, paymentId, PaymentCheckType.COMPLIANCE_SCREENING, "compliance-api",
                Map.of("screenedAccounts", List.of("XXXX7712"), "proposedAccount", "XXXX9931"),
                "scr-2026-10-21", EVALUATED_AT.plusSeconds(3600), Freshness.INCOMPLETE));
        reloadFromDatabase();

        assertThat(evidence.findByPaymentId(paymentId))
                .extracting(EvidenceRecord::getId, EvidenceRecord::getFreshness)
                .containsExactlyInAnyOrder(
                        tuple(firstAnswer, Freshness.UNAVAILABLE),
                        tuple(secondAnswer, Freshness.INCOMPLETE));
        assertThat(evidence.findById(secondAnswer).orElseThrow().getValue())
                .containsEntry("screenedAccounts", List.of("XXXX7712"));
    }

    // Use case: INV-94018 is paid in two instalments, OBL-1 and OBL-2, each its
    // own payment. Evaluating one must load only its own evidence, or a fresh
    // funds answer for one instalment could approve the other.
    @Test
    void findByPaymentIdReturnsOnlyThatPaymentsEvidence() {
        UUID firstInstalment = insertPayment("OBL-1");
        UUID secondInstalment = insertPayment("OBL-2");

        evidence.saveAll(scenarioSnapshot(firstInstalment));
        evidence.save(new EvidenceRecord(UUID.randomUUID(), secondInstalment, PaymentCheckType.AVAILABLE_FUNDS, "treasury-ledger",
                Map.of("availablePaise", 3_40_00_000_00L), "ledger-881", EVALUATED_AT, Freshness.FRESH));
        reloadFromDatabase();

        assertThat(evidence.findByPaymentId(firstInstalment)).hasSize(6);
        assertThat(evidence.findByPaymentId(secondInstalment))
                .singleElement()
                .extracting(EvidenceRecord::getPaymentCheckType)
                .isEqualTo(PaymentCheckType.AVAILABLE_FUNDS);
    }

    // The six answers from the guide's scenario table.
    private List<EvidenceRecord> scenarioSnapshot(UUID paymentId) {
        return List.of(
                record(paymentId, PaymentCheckType.APPROVAL_AUTHORITY, "delegation-registry",
                        Map.of("active", true, "limitPaise", 50_00_000_00L), "del-7", Freshness.FRESH),
                record(paymentId, PaymentCheckType.INVOICE, "procurement",
                        Map.of("reference", "INV-94018", "amountPaise", 48_75_000_00L, "currency", "INR", "status", "APPROVED"),
                        "inv-3", Freshness.FRESH),
                record(paymentId, PaymentCheckType.BENEFICIARY_ACCOUNT, "vendor-master",
                        Map.of("approvedFingerprint", "BANK-A-XXXX7712", "pendingFingerprint", "BANK-B-XXXX9931"),
                        "vm-v42", Freshness.FRESH),
                record(paymentId, PaymentCheckType.COMPLIANCE_SCREENING, "compliance-api",
                        Map.of("cause", "HTTP_503"), null, Freshness.UNAVAILABLE),
                record(paymentId, PaymentCheckType.DUPLICATE_PAYMENT, "duplicate-service",
                        Map.of("result", "NO_DUPLICATE_FOUND", "consumerLagSeconds", 17 * 60), "dup-offset-55120",
                        Freshness.STALE),
                record(paymentId, PaymentCheckType.AVAILABLE_FUNDS, "treasury-ledger",
                        Map.of("availablePaise", 3_40_00_000_00L), "ledger-880", Freshness.FRESH));
    }

    private EvidenceRecord record(UUID paymentId, PaymentCheckType checkType, String source, Map<String, Object> value,
                                  String sourceVersion, Freshness freshness) {
        Instant sourceTime = freshness == Freshness.UNAVAILABLE ? null : EVALUATED_AT;
        return new EvidenceRecord(UUID.randomUUID(), paymentId, checkType, source, value,
                sourceVersion, sourceTime, freshness);
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
