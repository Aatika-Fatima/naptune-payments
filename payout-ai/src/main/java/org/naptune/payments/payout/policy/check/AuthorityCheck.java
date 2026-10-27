package org.naptune.payments.payout.policy.check;

import org.naptune.payments.payout.entity.SupplierPayment;
import org.naptune.payments.payout.policy.*;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(1)
public class AuthorityCheck implements PolicyCheck {

    @Override
    public CheckResult apply(SupplierPayment payment, EvidenceSnapshot evidence) {
        Evidence delegation = evidence.get(PaymentCheckType.APPROVAL_AUTHORITY);
        if (delegation.freshness() == Freshness.CONFLICTING) {
            return new CheckResult.Investigate("APPROVAL_AUTHORITY_EVIDENCE_CONFLICTING");
        }
        if (!delegation.usable()) {
            return new CheckResult.Defer("AUTHORITY_EVIDENCE_UNAVAILABLE");
        }
        if (!Boolean.TRUE.equals(delegation.value().get("active"))) {
            return new CheckResult.Block("DELEGATION_INACTIVE");
        }
        // The limit is inclusive: a payment of exactly the limit is within authority.
        if (!(delegation.value().get("limitPaise") instanceof Number limit)
                || payment.getAmountPaise() > limit.longValue()) {
            return new CheckResult.Block("AUTHORITY_LIMIT_EXCEEDED");
        }
        return new CheckResult.Pass();
    }
}
