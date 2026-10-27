package org.naptune.payments.payout.policy.check;

import org.naptune.payments.payout.entity.SupplierPayment;
import org.naptune.payments.payout.policy.*;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(5)
public class DuplicateCheck implements PolicyCheck {

    @Override
    public CheckResult apply(SupplierPayment payment, EvidenceSnapshot evidence) {
        Evidence duplicates = evidence.get(PaymentCheckType.DUPLICATE_PAYMENT);
        // Whatever the result says: "none found" from a lagging consumer is not
        // evidence of absence.
        if (duplicates.freshness() == Freshness.STALE) {
            return new CheckResult.Defer("DUPLICATE_CHECK_STALE");
        }
        if (duplicates.freshness() == Freshness.CONFLICTING) {
            return new CheckResult.Investigate("DUPLICATE_PAYMENT_EVIDENCE_CONFLICTING");
        }
        if (!duplicates.usable()) {
            return new CheckResult.Defer("DUPLICATE_CHECK_UNAVAILABLE");
        }
        Object result = duplicates.value().get("result");
        if ("DUPLICATE_FOUND".equals(result)) {
            return new CheckResult.Block("DUPLICATE_FOUND");
        }
        // Only an explicit "none found" passes; an answer we don't recognise is not one.
        if (!"NO_DUPLICATE_FOUND".equals(result)) {
            return new CheckResult.Defer("DUPLICATE_CHECK_UNAVAILABLE");
        }
        return new CheckResult.Pass();
    }
}
