package org.naptune.payments.payout.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.naptune.payments.payout.entity.TestPayments.paymentIn;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.naptune.payments.payout.domain.exception.PayoutException.IllegalPaymentTransitionException;
import org.naptune.payments.payout.entity.SupplierPayment;

// VALIDATING → DEFERRED (only deferrals). The bank change is approved, so the
// beneficiary check passes, but compliance still returns 503. Decision 2
// holds only COMPLIANCE_EVIDENCE_UNAVAILABLE: no block, so the payment waits.
@DisplayName("VALIDATING → DEFERRED: no block, but compliance is still unavailable")
class DeferTransitionCases {

    private static final String DECISION_2 = "decision 2: COMPLIANCE_EVIDENCE_UNAVAILABLE";

    private final SupplierPayment payment = paymentIn(PaymentStatus.VALIDATING);

    @Test
    @DisplayName("a validating payment is deferred")
    void validatingPaymentIsDeferred() {
        PaymentTransitions.transition(payment, PaymentStatus.DEFERRED, DECISION_2);

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.DEFERRED);
    }

    @Test
    @DisplayName("a blocked payment, re-evaluated after the bank change, is deferred")
    void reEvaluatedBlockedPaymentCanBeDeferred() {
        SupplierPayment blocked = paymentIn(PaymentStatus.BLOCKED);

        PaymentTransitions.transition(blocked, PaymentStatus.VALIDATING, "re-evaluate after bank change approved");
        PaymentTransitions.transition(blocked, PaymentStatus.DEFERRED, DECISION_2);

        assertThat(blocked.getStatus()).isEqualTo(PaymentStatus.DEFERRED);
    }

    @Test
    @DisplayName("a deferred payment cannot jump to AUTHORIZED when compliance returns")
    void deferredPaymentCannotBeAuthorizedWhenComplianceReturns() {
        PaymentTransitions.transition(payment, PaymentStatus.DEFERRED, DECISION_2);

        assertThatThrownBy(() -> PaymentTransitions.transition(payment, PaymentStatus.AUTHORIZED, "compliance CLEAR"))
                .isInstanceOf(IllegalPaymentTransitionException.class)
                .hasMessage("Illegal transition: DEFERRED -> AUTHORIZED (compliance CLEAR)");
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.DEFERRED);
    }

    @Test
    @DisplayName("a deferred payment cannot be blocked without a new evaluation")
    void deferredPaymentCannotBeBlockedWithoutAnEvaluation() {
        PaymentTransitions.transition(payment, PaymentStatus.DEFERRED, DECISION_2);

        assertThatThrownBy(() -> PaymentTransitions.transition(payment, PaymentStatus.BLOCKED, "BENEFICIARY_CHANGE_PENDING"))
                .isInstanceOf(IllegalPaymentTransitionException.class)
                .hasMessage("Illegal transition: DEFERRED -> BLOCKED (BENEFICIARY_CHANGE_PENDING)");
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.DEFERRED);
    }

    @Test
    @DisplayName("a deferred payment leaves only by re-evaluation")
    void deferredPaymentCanOnlyLeaveByReEvaluation() {
        PaymentTransitions.transition(payment, PaymentStatus.DEFERRED, DECISION_2);

        PaymentTransitions.transition(payment, PaymentStatus.VALIDATING, "re-evaluate");

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.VALIDATING);
    }
}
