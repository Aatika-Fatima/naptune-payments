package org.naptune.payments.payout.policy;

import org.naptune.payments.payout.entity.SupplierPayment;

public interface PolicyCheck {
    CheckResult apply(SupplierPayment payment, EvidenceSnapshot evidence);
}