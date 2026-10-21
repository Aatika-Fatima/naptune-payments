package org.naptune.payments.payout.domain;

// Shared by the *TransitionCases classes, run through PaymentTransitionsTest.
final class TestPayments {

    private TestPayments() {
    }

    // The protected constructor is reachable from this package; in the app,
    // rows come only from the repository's insert-or-load.
    static SupplierPayment paymentIn(PaymentStatus status) {
        SupplierPayment payment = new SupplierPayment();
        payment.setStatus(status);
        return payment;
    }
}
