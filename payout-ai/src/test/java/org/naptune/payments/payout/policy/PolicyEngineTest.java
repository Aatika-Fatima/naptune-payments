package org.naptune.payments.payout.policy;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.naptune.payments.payout.entity.SupplierPayment;
import org.naptune.payments.payout.entity.TestPayments;
import org.naptune.payments.payout.policy.check.AuthorityCheck;
import org.naptune.payments.payout.policy.check.BeneficiaryCheck;
import org.naptune.payments.payout.policy.check.ComplianceCheck;
import org.naptune.payments.payout.policy.check.DuplicateCheck;
import org.naptune.payments.payout.policy.check.FundsCheck;
import org.naptune.payments.payout.policy.check.InvoiceCheck;

// Plain unit test with hand-built snapshots: the six real checks in @Order
// order, no Spring, no sources.
class PolicyEngineTest {

    private static final Instant NOW = Instant.parse("2026-10-22T09:30:00Z");
    private static final long AMOUNT = 4_875_000_00L;
    private static final String APPROVED_ACCOUNT = "BANK-A-XXXX7712";
    private static final String EMAIL_ACCOUNT = "BANK-B-XXXX9931";

    private static final List<PolicyCheck> CHECKS = List.of(new AuthorityCheck(), new InvoiceCheck(),
            new BeneficiaryCheck(), new ComplianceCheck(), new DuplicateCheck(), new FundsCheck());

    private final PolicyEngine engine = new PolicyEngine(CHECKS);
    private final SupplierPayment toEmailAccount = TestPayments.proposal("SUP-1", "INV94018", AMOUNT, EMAIL_ACCOUNT);
    private final SupplierPayment toApprovedAccount = TestPayments.proposal("SUP-1", "INV94018", AMOUNT, APPROVED_ACCOUNT);

    @Test
    void scenarioAtNineThirtyBlocksWithTheThreeCodesInOrder() {
        EvidenceSnapshot snapshot = scenario();

        PaymentDecision decision = engine.evaluate(toEmailAccount, snapshot);

        assertThat(decision.decision()).isEqualTo(Decision.BLOCK);
        assertThat(decision.reasonCodes()).containsExactly(
                "BENEFICIARY_CHANGE_PENDING", "COMPLIANCE_EVIDENCE_UNAVAILABLE", "DUPLICATE_CHECK_STALE");
        assertThat(decision.executionAllowed()).isFalse();
        assertThat(decision.evidenceIds()).containsExactlyElementsOf(snapshot.ids());
    }

    @Test
    void everyCheckRunsAfterTheFirstBlock() {
        // Blocks first, then defers, each group in check order, although the
        // defer (check 4) comes before the second block (check 6).
        EvidenceSnapshot snapshot = with(scenario(),
                fresh(PaymentCheckType.DUPLICATE_PAYMENT, "duplicate-service", Map.of("result", "NO_DUPLICATE_FOUND")),
                fresh(PaymentCheckType.AVAILABLE_FUNDS, "treasury-ledger", Map.of("availablePaise", 100L)));

        PaymentDecision decision = engine.evaluate(toEmailAccount, snapshot);

        assertThat(decision.decision()).isEqualTo(Decision.BLOCK);
        assertThat(decision.reasonCodes()).containsExactly(
                "BENEFICIARY_CHANGE_PENDING", "INSUFFICIENT_FUNDS", "COMPLIANCE_EVIDENCE_UNAVAILABLE");
    }

    @Test
    void allSixFreshAndPassingApproves() {
        PaymentDecision decision = engine.evaluate(toApprovedAccount, allPassing());

        assertThat(decision.decision()).isEqualTo(Decision.APPROVE);
        assertThat(decision.reasonCodes()).isEmpty();
        assertThat(decision.executionAllowed()).isTrue();
    }

    @Test
    void onlyUnknownsDefer() {
        EvidenceSnapshot snapshot = with(allPassing(),
                Evidence.unavailable(PaymentCheckType.COMPLIANCE_SCREENING, "compliance-api", "ExecutionException"));

        PaymentDecision decision = engine.evaluate(toApprovedAccount, snapshot);

        assertThat(decision.decision()).isEqualTo(Decision.DEFER);
        assertThat(decision.reasonCodes()).containsExactly("COMPLIANCE_EVIDENCE_UNAVAILABLE");
    }

    @Test
    void conflictOutranksDeferButNotBlock() {
        Evidence conflicting = new Evidence(UUID.randomUUID(), PaymentCheckType.INVOICE, "procurement",
                Freshness.CONFLICTING, Map.of("found", true), null, NOW);
        EvidenceSnapshot deferAndConflict = with(allPassing(), conflicting,
                Evidence.unavailable(PaymentCheckType.COMPLIANCE_SCREENING, "compliance-api", "ExecutionException"));

        PaymentDecision investigate = engine.evaluate(toApprovedAccount, deferAndConflict);
        PaymentDecision block = engine.evaluate(toEmailAccount, deferAndConflict);

        assertThat(investigate.decision()).isEqualTo(Decision.INVESTIGATE);
        assertThat(investigate.reasonCodes()).containsExactly("INVOICE_EVIDENCE_CONFLICTING", "COMPLIANCE_EVIDENCE_UNAVAILABLE");
        assertThat(block.decision()).isEqualTo(Decision.BLOCK);
        assertThat(block.reasonCodes()).containsExactly(
                "BENEFICIARY_NOT_APPROVED", "INVOICE_EVIDENCE_CONFLICTING", "COMPLIANCE_EVIDENCE_UNAVAILABLE");
    }

    @Test
    void checkThatThrowsDefersAndNeverApproves() {
        List<PolicyCheck> checks = new ArrayList<>(CHECKS);
        checks.add((payment, evidence) -> {
            throw new IllegalStateException("bug in a rule");
        });

        PaymentDecision decision = new PolicyEngine(checks).evaluate(toApprovedAccount, allPassing());

        assertThat(decision.decision()).isEqualTo(Decision.DEFER);
        assertThat(decision.reasonCodes()).containsExactly("POLICY_CHECK_ERROR");
    }

    @Test
    void noEvidenceAtAllDefersOnEveryCheck() {
        Map<PaymentCheckType, Evidence> items = new EnumMap<>(PaymentCheckType.class);
        for (PaymentCheckType check : PaymentCheckType.values()) {
            items.put(check, Evidence.unavailable(check, "none", "NO_SOURCE_REGISTERED"));
        }

        PaymentDecision decision = engine.evaluate(toApprovedAccount, new EvidenceSnapshot(items, NOW));

        assertThat(decision.decision()).isEqualTo(Decision.DEFER);
        assertThat(decision.reasonCodes()).containsExactly("AUTHORITY_EVIDENCE_UNAVAILABLE",
                "PROCUREMENT_EVIDENCE_UNAVAILABLE", "VENDOR_MASTER_UNAVAILABLE", "COMPLIANCE_EVIDENCE_UNAVAILABLE",
                "DUPLICATE_CHECK_UNAVAILABLE", "LEDGER_UNAVAILABLE");
    }

    @Test
    void sameEvidenceGivesTheSameDecision() {
        EvidenceSnapshot snapshot = scenario();

        assertThat(engine.evaluate(toEmailAccount, snapshot)).isEqualTo(engine.evaluate(toEmailAccount, snapshot));
    }

    // The guide's 09:30 table: authority, invoice and funds pass; vendor master
    // shows the email's account as a pending change; compliance is down; the
    // duplicate consumer is 17 min behind.
    private static EvidenceSnapshot scenario() {
        return snapshot(
                fresh(PaymentCheckType.APPROVAL_AUTHORITY, "delegation-registry",
                        Map.of("active", true, "limitPaise", 5_000_000_00L)),
                fresh(PaymentCheckType.INVOICE, "procurement", Map.of("found", true, "reference", "INV-94018",
                        "amountPaise", AMOUNT, "currency", "INR", "status", "APPROVED")),
                fresh(PaymentCheckType.BENEFICIARY_ACCOUNT, "vendor-master",
                        Map.of("approvedFingerprint", APPROVED_ACCOUNT, "pendingFingerprint", EMAIL_ACCOUNT)),
                Evidence.unavailable(PaymentCheckType.COMPLIANCE_SCREENING, "compliance-api", "ExecutionException"),
                new Evidence(UUID.randomUUID(), PaymentCheckType.DUPLICATE_PAYMENT, "duplicate-service", Freshness.STALE,
                        Map.of("result", "NO_DUPLICATE_FOUND", "kafkaConsumerProcessingDelaySeconds", 1020L),
                        "offset-881", NOW.minusSeconds(1020)),
                fresh(PaymentCheckType.AVAILABLE_FUNDS, "treasury-ledger", Map.of("availablePaise", 34_000_000_00L)));
    }

    private static EvidenceSnapshot allPassing() {
        return with(scenario(),
                fresh(PaymentCheckType.BENEFICIARY_ACCOUNT, "vendor-master", Map.of("approvedFingerprint", APPROVED_ACCOUNT)),
                fresh(PaymentCheckType.COMPLIANCE_SCREENING, "compliance-api", Map.of("result", "SCREENING_PASSED")),
                fresh(PaymentCheckType.DUPLICATE_PAYMENT, "duplicate-service", Map.of("result", "NO_DUPLICATE_FOUND")));
    }

    private static EvidenceSnapshot with(EvidenceSnapshot base, Evidence... replacements) {
        Map<PaymentCheckType, Evidence> items = new EnumMap<>(PaymentCheckType.class);
        items.putAll(base.items());
        for (Evidence e : replacements) {
            items.put(e.paymentCheckType(), e);
        }
        return new EvidenceSnapshot(items, NOW);
    }

    private static EvidenceSnapshot snapshot(Evidence... evidence) {
        return with(new EvidenceSnapshot(new EnumMap<>(PaymentCheckType.class), NOW), evidence);
    }

    private static Evidence fresh(PaymentCheckType check, String source, Map<String, Object> value) {
        return new Evidence(UUID.randomUUID(), check, source, Freshness.FRESH, value, "v1", NOW);
    }
}
