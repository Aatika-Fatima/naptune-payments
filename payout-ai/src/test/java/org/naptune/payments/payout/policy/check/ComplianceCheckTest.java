package org.naptune.payments.payout.policy.check;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.naptune.payments.payout.entity.SupplierPayment;
import org.naptune.payments.payout.entity.TestPayments;
import org.naptune.payments.payout.policy.CheckResult;
import org.naptune.payments.payout.policy.Evidence;
import org.naptune.payments.payout.policy.EvidenceSnapshot;
import org.naptune.payments.payout.policy.Freshness;
import org.naptune.payments.payout.policy.PaymentCheckType;

class ComplianceCheckTest {

    private static final Instant NOW = Instant.parse("2026-10-22T09:30:00Z");

    private final ComplianceCheck check = new ComplianceCheck();
    private final SupplierPayment payment = TestPayments.proposal("SUP-1", "INV94018", 100_00L, "XXXX9931");

    @Test
    void clearanceOfEveryPartyPasses() {
        assertThat(apply(screening(Freshness.FRESH, "SCREENING_PASSED"))).isEqualTo(new CheckResult.Pass());
    }

    @Test
    void riskDetectedBlocks() {
        assertThat(apply(screening(Freshness.FRESH, "RISK_DETECTED")))
                .isEqualTo(new CheckResult.Block("COMPLIANCE_RISK_DETECTED"));
    }

    @Test
    void unscreenedAccountDefers() {
        assertThat(apply(screening(Freshness.INCOMPLETE, "SCREENING_INCOMPLETE")))
                .isEqualTo(new CheckResult.Defer("COMPLIANCE_SCREENING_INCOMPLETE"));
    }

    @Test
    void unavailableApiDefersAndIsNotAClearance() {
        Evidence down = Evidence.unavailable(PaymentCheckType.COMPLIANCE_SCREENING, "compliance-api", "ExecutionException");

        assertThat(apply(down)).isEqualTo(new CheckResult.Defer("COMPLIANCE_EVIDENCE_UNAVAILABLE"));
    }

    @Test
    void staleClearanceDefers() {
        assertThat(apply(screening(Freshness.STALE, "SCREENING_PASSED")))
                .isEqualTo(new CheckResult.Defer("COMPLIANCE_EVIDENCE_UNAVAILABLE"));
    }

    @Test
    void unrecognisedResultDoesNotPass() {
        assertThat(apply(screening(Freshness.FRESH, "SOMETHING_NEW")))
                .isEqualTo(new CheckResult.Defer("COMPLIANCE_EVIDENCE_UNAVAILABLE"));
    }

    private CheckResult apply(Evidence compliance) {
        Map<PaymentCheckType, Evidence> items = new EnumMap<>(PaymentCheckType.class);
        items.put(PaymentCheckType.COMPLIANCE_SCREENING, compliance);
        return check.apply(payment, new EvidenceSnapshot(items, NOW));
    }

    private static Evidence screening(Freshness freshness, String result) {
        return new Evidence(UUID.randomUUID(), PaymentCheckType.COMPLIANCE_SCREENING, "compliance-api", freshness,
                Map.of("result", result, "detectedRisks", List.of(), "partiesAwaitingScreening", List.of()),
                "scr-2026-10-21", NOW);
    }
}
