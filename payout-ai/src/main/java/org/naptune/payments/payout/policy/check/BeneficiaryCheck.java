package org.naptune.payments.payout.policy.check;

import org.naptune.payments.payout.entity.SupplierPayment;
import org.naptune.payments.payout.policy.*;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(3)
public class BeneficiaryCheck implements PolicyCheck {

    @Override
    public CheckResult apply(SupplierPayment payment, EvidenceSnapshot evidence) {
        Evidence vendor = evidence.get(PaymentCheckType.BENEFICIARY_ACCOUNT);
        // Two approved accounts at once: waiting will not fix it, a person must look.
        if (vendor.freshness() == Freshness.CONFLICTING) {
            return new CheckResult.Investigate("BENEFICIARY_ACCOUNT_EVIDENCE_CONFLICTING");
        }
        if (!vendor.usable()) {
            return new CheckResult.Defer("VENDOR_MASTER_UNAVAILABLE");
        }
        String proposed = payment.getBeneficiaryFingerprint();
        if (proposed.equals(vendor.value().get("approvedFingerprint"))) {
            return new CheckResult.Pass();
        }
        // The account in the agent's email is a change request awaiting its
        // second approval. That is a pending change, not an approval.
        if (proposed.equals(vendor.value().get("pendingFingerprint"))) {
            return new CheckResult.Block("BENEFICIARY_CHANGE_PENDING");
        }
        return new CheckResult.Block("BENEFICIARY_NOT_APPROVED");
    }
}