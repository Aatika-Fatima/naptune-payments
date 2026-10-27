package org.naptune.payments.payout.domain.exception;

import java.util.UUID;
import org.naptune.payments.payout.domain.PaymentStatus;

public final class PayoutException {

    private PayoutException() {
    }

    // 400. Blank after normalisation, or longer than the 64-character column.
    public static class InvalidInvoiceReferenceException extends RuntimeException {
        public InvalidInvoiceReferenceException(String raw) {
            super("Invalid invoice reference: " + raw);
        }
    }

    // Thrown by PaymentTransitions for any move not in its table.
    public static class IllegalPaymentTransitionException extends RuntimeException {
        public IllegalPaymentTransitionException(String message) {
            super(message);
        }
    }

    // 409. Same economic action as an existing payment, but a different amount,
    // currency or beneficiary. Never merged, and never a second payment.
    public static class EconomicActionConflictException extends RuntimeException {
        private final UUID canonicalPaymentId;

        public EconomicActionConflictException(UUID canonicalPaymentId) {
            super("Conflicts with the terms of existing payment " + canonicalPaymentId);
            this.canonicalPaymentId = canonicalPaymentId;
        }

        public UUID getCanonicalPaymentId() {
            return canonicalPaymentId;
        }
    }

    // 409. Same client and Idempotency-Key, different body: a caller bug, not a retry.
    public static class IdempotencyKeyReusedException extends RuntimeException {
        public IdempotencyKeyReusedException(String idempotencyKey) {
            super("Idempotency-Key reused with a different request: " + idempotencyKey);
        }
    }

    // 404.
    public static class PaymentNotFoundException extends RuntimeException {
        public PaymentNotFoundException(UUID paymentId) {
            super("Payment not found: " + paymentId);
        }
    }

    // 409. Re-evaluation asked of a payment in a state it cannot leave, e.g. AUTHORIZED.
    public static class EvaluationNotPossibleException extends RuntimeException {
        public EvaluationNotPossibleException(UUID paymentId, PaymentStatus status) {
            super("Payment " + paymentId + " cannot be evaluated from " + status);
        }
    }
}
