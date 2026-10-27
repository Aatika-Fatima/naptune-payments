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

/**
 * This TreasuryLedgerSource class checks
 * whether enough money is available in the treasury account to make a supplier payment.
 * It retrieves the latest available ledger snapshot, checks whether that snapshot is fresh,
 * and returns the result as evidence for the payment workflow.
 */
// The paying account's balance from a ledger snapshot. A snapshot older than
// the age limit is STALE: other payments may have drawn on it since.
@Component
public class TreasuryLedgerSource implements EvidenceSource {

    private final SimulatedWorld world;
    private final Clock clock;
    private final Duration maxAge;

    public TreasuryLedgerSource(SimulatedWorld world, Clock clock,
                                @Value("${payout.evidence.funds-max-age:60s}") Duration maxAge) {
        this.world = world;
        this.clock = clock;
        this.maxAge = maxAge;
    }

    @Override
    public PaymentCheckType paymentCheckType() {
        return PaymentCheckType.AVAILABLE_FUNDS;
    }

    @Override
    public String name() {
        return "treasury-ledger";
    }

    @Override
    public Evidence fetch(SupplierPayment payment) {

        // 1. Check if the ledger is available.
        world.throwIfFailing(paymentCheckType());

        // 2. Get the latest ledger snapshot.
        SimulatedWorld.Ledger ledger = world.ledger();

        // 3. Check how old the balance information is.
        Duration snapshotAge = Duration.between(
                ledger.observedAt,
                clock.instant()
        );

        // 4. Mark the balance information as fresh or stale.
        Freshness freshness = Freshness.FRESH;

        if (snapshotAge.compareTo(maxAge) > 0) {
            freshness = Freshness.STALE;
        }

        // 5. Return the balance evidence.
        return new Evidence(
                UUID.randomUUID(),
                paymentCheckType(),
                name(),
                freshness,
                Map.of("availablePaise", ledger.availablePaise),
                ledger.version,
                ledger.observedAt
        );
    }
}
