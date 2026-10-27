package org.naptune.payments.payout.simulation;

import java.time.Clock;
import java.util.Map;
import java.util.UUID;
import org.naptune.payments.payout.entity.SupplierPayment;
import org.naptune.payments.payout.policy.Evidence;
import org.naptune.payments.payout.policy.EvidenceSource;
import org.naptune.payments.payout.policy.Freshness;
import org.naptune.payments.payout.policy.PaymentCheckType;
import org.springframework.stereotype.Component;

// A live read of the delegation in force: always current when it answers.
@Component
public class DelegationRegistrySource implements EvidenceSource {

    private final SimulatedWorld world;
    private final Clock clock;

    public DelegationRegistrySource(SimulatedWorld world, Clock clock) {
        this.world = world;
        this.clock = clock;
    }

    @Override
    public PaymentCheckType paymentCheckType() {
        return PaymentCheckType.APPROVAL_AUTHORITY;
    }

    @Override
    public String name() {
        return "delegation-registry";
    }

    @Override
    public Evidence fetch(SupplierPayment payment) {
        world.throwIfFailing(paymentCheckType());
        SimulatedWorld.DelegationRegistry registry = world.delegation();
        return new Evidence(UUID.randomUUID(), paymentCheckType(), name(), Freshness.FRESH,
                Map.of("active", registry.active, "limitPaise", registry.limitPaise),
                registry.version, clock.instant());
    }
}
