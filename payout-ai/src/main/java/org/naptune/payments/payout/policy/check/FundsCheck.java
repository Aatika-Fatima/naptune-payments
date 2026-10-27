package org.naptune.payments.payout.policy.check;

import org.naptune.payments.payout.entity.SupplierPayment;
import org.naptune.payments.payout.policy.*;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(6)
public class FundsCheck implements PolicyCheck {

    @Override
    public CheckResult apply(SupplierPayment payment, EvidenceSnapshot evidence) {
        Evidence ledger = evidence.get(PaymentCheckType.AVAILABLE_FUNDS);
        // A balance older than its age limit may already have been spent.
        if (ledger.freshness() == Freshness.STALE) {
            return new CheckResult.Defer("FUNDS_SNAPSHOT_STALE");
        }
        if (ledger.freshness() == Freshness.CONFLICTING) {
            return new CheckResult.Investigate("AVAILABLE_FUNDS_EVIDENCE_CONFLICTING");
        }
        if (!ledger.usable() || !(ledger.value().get("availablePaise") instanceof Number available)) {
            return new CheckResult.Defer("LEDGER_UNAVAILABLE");
        }
        if (available.longValue() < payment.getAmountPaise()) {
            return new CheckResult.Block("INSUFFICIENT_FUNDS");
        }
        return new CheckResult.Pass();
    }
}
