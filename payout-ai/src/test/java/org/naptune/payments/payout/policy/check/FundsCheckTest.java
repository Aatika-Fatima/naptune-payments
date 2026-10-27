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

class FundsCheckTest {

    private static final Instant NOW = Instant.parse("2026-10-22T09:30:00Z");
    private static final long AMOUNT = 4_875_000_00L;

    private final FundsCheck check = new FundsCheck();
    private final SupplierPayment payment = TestPayments.proposal("SUP-1", "INV94018", AMOUNT, "XXXX7712");

    @Test
    void enoughFundsPasses() {
        assertThat(apply(ledger(Freshness.FRESH, 34_000_000_00L))).isEqualTo(new CheckResult.Pass());
    }

    @Test
    void balanceEqualToAmountPasses() {
        assertThat(apply(ledger(Freshness.FRESH, AMOUNT))).isEqualTo(new CheckResult.Pass());
    }

    @Test
    void balanceBelowAmountBlocks() {
        assertThat(apply(ledger(Freshness.FRESH, AMOUNT - 1)))
                .isEqualTo(new CheckResult.Block("INSUFFICIENT_FUNDS"));
    }

    @Test
    void staleSnapshotDefersEvenWithEnoughFunds() {
        assertThat(apply(ledger(Freshness.STALE, 34_000_000_00L)))
                .isEqualTo(new CheckResult.Defer("FUNDS_SNAPSHOT_STALE"));
    }

    @Test
    void unavailableLedgerDefers() {
        Evidence down = Evidence.unavailable(PaymentCheckType.AVAILABLE_FUNDS, "treasury-ledger", "ExecutionException");

        assertThat(apply(down)).isEqualTo(new CheckResult.Defer("LEDGER_UNAVAILABLE"));
    }

    private CheckResult apply(Evidence ledger) {
        Map<PaymentCheckType, Evidence> items = new EnumMap<>(PaymentCheckType.class);
        items.put(PaymentCheckType.AVAILABLE_FUNDS, ledger);
        return check.apply(payment, new EvidenceSnapshot(items, NOW));
    }

    private static Evidence ledger(Freshness freshness, long availablePaise) {
        return new Evidence(UUID.randomUUID(), PaymentCheckType.AVAILABLE_FUNDS, "treasury-ledger", freshness,
                Map.of("availablePaise", availablePaise), "ledger-9", NOW.minusSeconds(20));
    }
}
