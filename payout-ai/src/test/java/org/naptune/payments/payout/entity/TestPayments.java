package org.naptune.payments.payout.entity;

import org.naptune.payments.payout.domain.PaymentStatus;

// Shared by the *TransitionCases classes, run through PaymentTransitionsTest.
public final class TestPayments {

    private TestPayments() {
    }

    // Lives in the entity package so it can reach the protected constructor; in the app,
    // rows come only from the repository's insert-or-load.
    public static SupplierPayment paymentIn(PaymentStatus status) {
        SupplierPayment payment = new SupplierPayment();
        payment.setStatus(status);
        return payment;
    }
}
