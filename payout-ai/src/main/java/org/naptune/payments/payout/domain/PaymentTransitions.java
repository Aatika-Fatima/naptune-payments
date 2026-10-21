package org.naptune.payments.payout.domain;

import static org.naptune.payments.payout.domain.PaymentStatus.AUTHORIZED;
import static org.naptune.payments.payout.domain.PaymentStatus.BLOCKED;
import static org.naptune.payments.payout.domain.PaymentStatus.DEFERRED;
import static org.naptune.payments.payout.domain.PaymentStatus.PROPOSED;
import static org.naptune.payments.payout.domain.PaymentStatus.VALIDATING;

import java.util.Map;
import java.util.Set;
import org.naptune.payments.payout.domain.exception.PayoutException.IllegalPaymentTransitionException;
import org.naptune.payments.payout.entity.SupplierPayment;

public final class PaymentTransitions {

    private static final Map<PaymentStatus, Set<PaymentStatus>> ALLOWED = Map.of(
            PROPOSED, Set.of(VALIDATING),
            VALIDATING, Set.of(BLOCKED, DEFERRED, AUTHORIZED),
            BLOCKED, Set.of(VALIDATING),
            DEFERRED, Set.of(VALIDATING),
            AUTHORIZED, Set.of());          // DISPATCH_PENDING arrives with the dispatch use case

    private PaymentTransitions() {
    }

    // The domain rule only. Concurrency is the persistence layer's job: the
    // caller holds the row lock, and @Version catches anyone who does not.
    public static void transition(SupplierPayment payment, PaymentStatus next, String reason) {
        if (!ALLOWED.getOrDefault(payment.getStatus(), Set.of()).contains(next)) {
            throw new IllegalPaymentTransitionException(
                    "Illegal transition: " + payment.getStatus() + " -> " + next + " (" + reason + ")");
        }
        payment.setStatus(next);
    }
}
