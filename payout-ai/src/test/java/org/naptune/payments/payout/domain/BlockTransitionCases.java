package org.naptune.payments.payout.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.naptune.payments.payout.domain.TestPayments.paymentIn;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.naptune.payments.payout.domain.exception.PayoutException.IllegalPaymentTransitionException;

// VALIDATING → BLOCKED (any block). Decision 1 carries BENEFICIARY_CHANGE_PENDING
// (XXXX9931 is PENDING_SECOND_APPROVAL) and COMPLIANCE_EVIDENCE_UNAVAILABLE.
// The policy engine picks BLOCK over DEFER; here the payment must stay
// blocked until someone re-evaluates.
@DisplayName("VALIDATING → BLOCKED: the bank change is still pending and the block wins")
class BlockTransitionCases {

    private static final String DECISION_1 =
            "decision 1: BENEFICIARY_CHANGE_PENDING, COMPLIANCE_EVIDENCE_UNAVAILABLE";

    private final SupplierPayment payment = paymentIn(PaymentStatus.VALIDATING);

    @Test
    @DisplayName("a validating payment is blocked")
    void validatingPaymentIsBlocked() {
        PaymentTransitions.transition(payment, PaymentStatus.BLOCKED, DECISION_1);

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.BLOCKED);
    }

    @Test
    @DisplayName("the compliance deferral cannot replace the block")
    void theComplianceDeferralCannotReplaceTheBlock() {
        PaymentTransitions.transition(payment, PaymentStatus.BLOCKED, DECISION_1);

        assertThatThrownBy(() -> PaymentTransitions.transition(payment, PaymentStatus.DEFERRED, "COMPLIANCE_EVIDENCE_UNAVAILABLE"))
                .isInstanceOf(IllegalPaymentTransitionException.class)
                .hasMessage("Illegal transition: BLOCKED -> DEFERRED (COMPLIANCE_EVIDENCE_UNAVAILABLE)");
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.BLOCKED);
    }

    @Test
    @DisplayName("a blocked payment cannot jump straight to AUTHORIZED")
    void blockedPaymentCannotBeAuthorizedDirectly() {
        PaymentTransitions.transition(payment, PaymentStatus.BLOCKED, DECISION_1);

        assertThatThrownBy(() -> PaymentTransitions.transition(payment, PaymentStatus.AUTHORIZED, "bank change approved"))
                .isInstanceOf(IllegalPaymentTransitionException.class)
                .hasMessage("Illegal transition: BLOCKED -> AUTHORIZED (bank change approved)");
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.BLOCKED);
    }

    @Test
    @DisplayName("a blocked payment leaves only by re-evaluation")
    void blockedPaymentCanOnlyLeaveByReEvaluation() {
        PaymentTransitions.transition(payment, PaymentStatus.BLOCKED, DECISION_1);

        PaymentTransitions.transition(payment, PaymentStatus.VALIDATING, "re-evaluate");

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.VALIDATING);
    }
}
