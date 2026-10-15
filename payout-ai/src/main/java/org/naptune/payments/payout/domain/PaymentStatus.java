package org.naptune.payments.payout.domain;

// UC-01's states. DISPATCH_PENDING through RECONCILED and RETURNED arrive with
// the dispatch use case (UC-04). Stored by name in payment.status.
public enum PaymentStatus {
    PROPOSED, VALIDATING, BLOCKED, DEFERRED, AUTHORIZED
}
