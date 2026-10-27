package org.naptune.payments.payout.policy.check;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.EnumMap;
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

class AuthorityCheckTest {

    private static final Instant NOW = Instant.parse("2026-10-22T09:30:00Z");
    private static final long AMOUNT = 4_875_000_00L;
    private static final long LIMIT = 5_000_000_00L;

    private final AuthorityCheck check = new AuthorityCheck();
    private final SupplierPayment payment = TestPayments.proposal("SUP-1", "INV94018", AMOUNT, "XXXX7712");

    @Test
    void activeDelegationWithinLimitPasses() {
        assertThat(apply(payment, delegation(Freshness.FRESH, true, LIMIT))).isEqualTo(new CheckResult.Pass());
    }

    @Test
    void amountEqualToLimitPasses() {
        assertThat(apply(payment, delegation(Freshness.FRESH, true, AMOUNT))).isEqualTo(new CheckResult.Pass());
    }

    @Test
    void amountAboveLimitBlocks() {
        assertThat(apply(payment, delegation(Freshness.FRESH, true, AMOUNT - 1)))
                .isEqualTo(new CheckResult.Block("AUTHORITY_LIMIT_EXCEEDED"));
    }

    @Test
    void inactiveDelegationBlocks() {
        assertThat(apply(payment, delegation(Freshness.FRESH, false, LIMIT)))
                .isEqualTo(new CheckResult.Block("DELEGATION_INACTIVE"));
    }

    @Test
    void unavailableRegistryDefers() {
        Evidence down = Evidence.unavailable(PaymentCheckType.APPROVAL_AUTHORITY, "delegation-registry", "ExecutionException");

        assertThat(apply(payment, down)).isEqualTo(new CheckResult.Defer("AUTHORITY_EVIDENCE_UNAVAILABLE"));
    }

    @Test
    void conflictingRegistryNeedsInvestigation() {
        assertThat(apply(payment, delegation(Freshness.CONFLICTING, true, LIMIT)))
                .isEqualTo(new CheckResult.Investigate("APPROVAL_AUTHORITY_EVIDENCE_CONFLICTING"));
    }

    private CheckResult apply(SupplierPayment payment, Evidence delegation) {
        Map<PaymentCheckType, Evidence> items = new EnumMap<>(PaymentCheckType.class);
        items.put(PaymentCheckType.APPROVAL_AUTHORITY, delegation);
        return check.apply(payment, new EvidenceSnapshot(items, NOW));
    }

    private static Evidence delegation(Freshness freshness, boolean active, long limitPaise) {
        return new Evidence(UUID.randomUUID(), PaymentCheckType.APPROVAL_AUTHORITY, "delegation-registry", freshness,
                Map.of("active", active, "limitPaise", limitPaise), "del-7", NOW);
    }
}
