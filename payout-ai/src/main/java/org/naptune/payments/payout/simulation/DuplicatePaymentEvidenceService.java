package org.naptune.payments.payout.simulation;

import java.time.Clock;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import org.naptune.payments.payout.entity.SupplierPayment;
import org.naptune.payments.payout.policy.Evidence;
import org.naptune.payments.payout.policy.EvidenceSource;
import org.naptune.payments.payout.policy.Freshness;
import org.naptune.payments.payout.policy.PaymentCheckType;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

// A consumer of the payment event stream. Its answer is true only as of the
// last offset it processed, so past the lag limit "none found" is not evidence
// of absence: STALE.

/**
 * Checks whether a supplier payment may be a duplicate.
 * The result is based on the events processed by the consumer.
 * If the consumer is too far behind, the evidence is considered stale.
 */
@Component
public class DuplicatePaymentEvidenceService implements EvidenceSource {

    private final SimulatedWorld simulationWorld;
    private final Clock clock;
    private final Duration maximumAllowedProcessingDelay;

    public DuplicatePaymentEvidenceService(
            SimulatedWorld simulationWorld,
            Clock clock,
            @Value("${payout.evidence.duplicate-max-lag:2m}")
            Duration maximumAllowedProcessingDelay) {

        this.simulationWorld = simulationWorld;
        this.clock = clock;
        this.maximumAllowedProcessingDelay =
                maximumAllowedProcessingDelay;
    }

    @Override
    public PaymentCheckType paymentCheckType() {
        return PaymentCheckType.DUPLICATE_PAYMENT;
    }

    @Override
    public String name() {
        // Stored in evidence_record.source; the guide's sample rows use this name.
        return "duplicate-service";
    }

    @Override
    public Evidence fetch(SupplierPayment supplierPayment) {

        simulationWorld.throwIfFailing(paymentCheckType());

        SimulatedWorld.Duplicates duplicateCheckStatus =
                simulationWorld.duplicates();

        Duration kafkaConsumerProcessingDelay =
                duplicateCheckStatus.lag;

        Freshness evidenceFreshness =
                kafkaConsumerProcessingDelay.compareTo(
                        maximumAllowedProcessingDelay) > 0
                        ? Freshness.STALE
                        : Freshness.FRESH;

        String duplicateCheckResult =
                duplicateCheckStatus.found
                        ? "DUPLICATE_FOUND"
                        : "NO_DUPLICATE_FOUND";

        return new Evidence(
                UUID.randomUUID(),
                paymentCheckType(),
                name(),
                evidenceFreshness,
                Map.of(
                        "result", duplicateCheckResult,
                        "kafkaConsumerProcessingDelaySeconds",
                        kafkaConsumerProcessingDelay.toSeconds()
                ),
                duplicateCheckStatus.offset,
                clock.instant().minus(kafkaConsumerProcessingDelay)
        );
    }
}
