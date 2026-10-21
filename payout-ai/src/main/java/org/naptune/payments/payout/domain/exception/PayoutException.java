package org.naptune.payments.payout.domain.exception;

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
}
