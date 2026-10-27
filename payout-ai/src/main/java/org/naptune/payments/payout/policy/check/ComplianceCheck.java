package org.naptune.payments.payout.policy.check;

import org.naptune.payments.payout.entity.SupplierPayment;
import org.naptune.payments.payout.policy.*;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(4)
public class ComplianceCheck implements PolicyCheck {

    @Override
    public CheckResult apply(SupplierPayment payment, EvidenceSnapshot evidence) {
        Evidence screening = evidence.get(PaymentCheckType.COMPLIANCE_SCREENING);
        // The API answered, but not for every party: a clearance of the supplier
        // and its old account says nothing about the account being paid.
        if (screening.freshness() == Freshness.INCOMPLETE) {
            return new CheckResult.Defer("COMPLIANCE_SCREENING_INCOMPLETE");
        }
        // A 503 is unavailable evidence, not a clearance.
        if (!screening.usable()) {
            return new CheckResult.Defer("COMPLIANCE_EVIDENCE_UNAVAILABLE");
        }
        Object result = screening.value().get("result");
        if ("RISK_DETECTED".equals(result)) {
            return new CheckResult.Block("COMPLIANCE_RISK_DETECTED");
        }
        // Only an explicit clearance passes; an answer we don't recognise is not one.
        if (!"SCREENING_PASSED".equals(result)) {
            return new CheckResult.Defer("COMPLIANCE_EVIDENCE_UNAVAILABLE");
        }
        return new CheckResult.Pass();
    }
}
