package org.naptune.payments.payout.entity;

import java.util.UUID;
import org.naptune.payments.payout.domain.PaymentStatus;
import org.springframework.test.util.ReflectionTestUtils;

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

    // The fields an EvidenceSource reads, for unit tests that need no database.
    public static SupplierPayment proposal(String supplierId, String normalizedInvoice, long amountPaise,
                                           String beneficiaryFingerprint) {
        SupplierPayment payment = new SupplierPayment();
        payment.setStatus(PaymentStatus.VALIDATING);
        ReflectionTestUtils.setField(payment, "id", UUID.randomUUID());
        ReflectionTestUtils.setField(payment, "supplierId", supplierId);
        ReflectionTestUtils.setField(payment, "normalizedInvoice", normalizedInvoice);
        ReflectionTestUtils.setField(payment, "amountPaise", amountPaise);
        ReflectionTestUtils.setField(payment, "currency", "INR");
        ReflectionTestUtils.setField(payment, "beneficiaryFingerprint", beneficiaryFingerprint);
        return payment;
    }
}
