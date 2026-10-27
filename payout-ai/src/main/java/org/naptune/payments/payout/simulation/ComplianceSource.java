package org.naptune.payments.payout.simulation;

import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.naptune.payments.payout.entity.SupplierPayment;
import org.naptune.payments.payout.policy.Evidence;
import org.naptune.payments.payout.policy.EvidenceSource;
import org.naptune.payments.payout.policy.Freshness;
import org.naptune.payments.payout.policy.PaymentCheckType;
import org.springframework.stereotype.Component;

// Screening covers a party, not a supplier's every account: yesterday's
// clearance of XXXX7712 says nothing about XXXX9931. So the answer is FRESH
// only when both the supplier and the account being paid were screened, or
// either is a hit; otherwise it is INCOMPLETE.
@Component
public class ComplianceSource implements EvidenceSource {

    private final SimulatedWorld world;
    private final Clock clock;

    public ComplianceSource(SimulatedWorld world, Clock clock) {
        this.world = world;
        this.clock = clock;
    }

    @Override
    public PaymentCheckType paymentCheckType() {
        return PaymentCheckType.COMPLIANCE_SCREENING;
    }

    @Override
    public String name() {
        return "compliance-api";
    }

    @Override
    public Evidence fetch(SupplierPayment payment) {
        world.throwIfFailing(paymentCheckType());
        SimulatedWorld.Compliance compliance = world.compliance();
        if (!compliance.up) {
            // Not this adapter's to absorb: the collector records it as UNAVAILABLE.
            throw new IllegalStateException("HTTP_503");
        }
        List<String> parties = List.of(payment.getSupplierId(), payment.getBeneficiaryFingerprint());
        List<String> detectedRisks = parties.stream().filter(compliance.hits::contains).toList();
        List<String> partiesAwaitingScreening = parties.stream().filter(p -> !compliance.screened.contains(p)).toList();

        Freshness freshness;
        String result;

        if (!detectedRisks.isEmpty()) {
            freshness = Freshness.FRESH;
            result = "RISK_DETECTED";

        } else if (partiesAwaitingScreening.isEmpty()) {
            freshness = Freshness.FRESH;
            result = "SCREENING_PASSED";

        } else {
            freshness = Freshness.INCOMPLETE;
            result = "SCREENING_INCOMPLETE";
        }
        return new Evidence(UUID.randomUUID(), paymentCheckType(), name(), freshness,
                Map.of("result", result, "detectedRisks", detectedRisks, "partiesAwaitingScreening", partiesAwaitingScreening,
                        "screenedAt", compliance.screenedAt.toString()),
                compliance.version, clock.instant());
    }
}
