package org.naptune.payments.payout.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.naptune.payments.payout.domain.TestPayments.paymentIn;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.naptune.payments.payout.domain.exception.PayoutException.IllegalPaymentTransitionException;

// PROPOSED → VALIDATING (claim). Agent A and Agent B propose the same
// invoice; B arrives 40 ms later. The row lock that serialises them is in
// DecisionOps; here B sees the payment as A left it.
@DisplayName("PROPOSED → VALIDATING: two agents claim the same payment")
class ClaimTransitionCases {

    private final SupplierPayment payment = paymentIn(PaymentStatus.PROPOSED);

    @Test
    @DisplayName("a proposed payment can be claimed")
    void proposedCanBeClaimed() {
        PaymentTransitions.transition(payment, PaymentStatus.VALIDATING, "claim");

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.VALIDATING);
    }

    @ParameterizedTest(name = "PROPOSED cannot move to {0}")
    @EnumSource(value = PaymentStatus.class, names = "VALIDATING", mode = EnumSource.Mode.EXCLUDE)
    @DisplayName("a proposed payment cannot go anywhere but VALIDATING")
    void proposedCannotGoAnywhereElse(PaymentStatus next) {
        assertThatThrownBy(() -> PaymentTransitions.transition(payment, next, "test"))
                .isInstanceOf(IllegalPaymentTransitionException.class)
                .hasMessage("Illegal transition: PROPOSED -> " + next + " (test)");
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PROPOSED);
    }

    @Test
    @DisplayName("agent A's claim moves the payment to VALIDATING")
    void agentAWinsTheClaim() {
        PaymentTransitions.transition(payment, PaymentStatus.VALIDATING, "claim by agent-a");

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.VALIDATING);
    }

    @Test
    @DisplayName("agent B's later claim is refused")
    void agentBCannotClaimWhatAgentAAlreadyHolds() {
        PaymentTransitions.transition(payment, PaymentStatus.VALIDATING, "claim by agent-a");

        assertThatThrownBy(() -> PaymentTransitions.transition(payment, PaymentStatus.VALIDATING, "claim by agent-b"))
                .isInstanceOf(IllegalPaymentTransitionException.class)
                .hasMessage("Illegal transition: VALIDATING -> VALIDATING (claim by agent-b)");
    }

    @Test
    @DisplayName("agent B's refusal names B's reason and leaves A's evaluation running")
    void agentBsLosingClaimLeavesAgentAsEvaluationInPlace() {
        PaymentTransitions.transition(payment, PaymentStatus.VALIDATING, "claim by agent-a");

        // B gets the payment back as it is and does not evaluate it.
        assertThatThrownBy(() -> PaymentTransitions.transition(payment, PaymentStatus.VALIDATING, "claim by agent-b"))
                .isInstanceOf(IllegalPaymentTransitionException.class)
                .hasMessageContaining("claim by agent-b")
                .hasMessageNotContaining("claim by agent-a");

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.VALIDATING);
    }
}
