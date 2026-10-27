package org.naptune.payments.payout.api;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.naptune.payments.payout.domain.PaymentStatus;
import org.naptune.payments.payout.entity.PaymentDecisionRecord;
import org.naptune.payments.payout.entity.SupplierPayment;
import org.naptune.payments.payout.policy.Decision;

// What a caller sees of a payment. No account or beneficiary field: a reader
// of the decision does not need it.
public record PaymentView(
        UUID paymentId,
        PaymentStatus status,
        Decision decision,
        List<String> reasonCodes,
        boolean executionAllowed,
        Instant decidedAt
) {

    // The latest decision is shown only while the payment is decided. During a
    // re-evaluation (VALIDATING) the previous decision no longer stands.
    public static PaymentView of(SupplierPayment payment, Optional<PaymentDecisionRecord> latest) {
        PaymentStatus status = payment.getStatus();
        Optional<PaymentDecisionRecord> current = decided(status) ? latest : Optional.empty();
        return new PaymentView(
                payment.getId(),
                status,
                current.map(PaymentDecisionRecord::getDecision).orElse(null),
                current.map(PaymentDecisionRecord::getReasonCodes).map(List::copyOf).orElse(List.of()),
                status == PaymentStatus.AUTHORIZED,
                current.map(PaymentDecisionRecord::getDecidedAt).orElse(null));
    }

    public boolean decided() {
        return decided(status);
    }

    private static boolean decided(PaymentStatus status) {
        return status == PaymentStatus.BLOCKED || status == PaymentStatus.DEFERRED
                || status == PaymentStatus.AUTHORIZED;
    }
}
