package org.naptune.payments.payout.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.naptune.payments.payout.entity.TestPayments.paymentIn;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.naptune.payments.payout.domain.exception.PayoutException.IllegalPaymentTransitionException;
import org.naptune.payments.payout.entity.SupplierPayment;

// BLOCKED → VALIDATING (re-evaluate). The second approver signs off the bank
// change with out-of-band verification, and finance ops calls
// POST /{id}/evaluate. The old decision stays; a new evaluation starts.
@DisplayName("BLOCKED → VALIDATING: finance ops re-evaluates after the bank change is approved")
class ReEvaluateTransitionCases {

    private static final String RE_EVALUATE =
            "re-evaluate by finance-ops: bank change approved with out-of-band verification";

    private final SupplierPayment payment = paymentIn(PaymentStatus.BLOCKED);

    @Test
    @DisplayName("a blocked payment goes back to VALIDATING")
    void blockedPaymentIsReEvaluated() {
        PaymentTransitions.transition(payment, PaymentStatus.VALIDATING, RE_EVALUATE);

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.VALIDATING);
    }

    @Test
    @DisplayName("a second re-evaluate while the first is running is refused")
    void secondReEvaluateWhileValidatingIsRefused() {
        PaymentTransitions.transition(payment, PaymentStatus.VALIDATING, RE_EVALUATE);

        assertThatThrownBy(() -> PaymentTransitions.transition(payment, PaymentStatus.VALIDATING, "re-evaluate again"))
                .isInstanceOf(IllegalPaymentTransitionException.class)
                .hasMessage("Illegal transition: VALIDATING -> VALIDATING (re-evaluate again)");
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.VALIDATING);
    }

    @ParameterizedTest(name = "the new evaluation can end {0}")
    @EnumSource(value = PaymentStatus.class, names = {"BLOCKED", "DEFERRED", "AUTHORIZED"})
    @DisplayName("the new evaluation can reach any outcome")
    void newEvaluationCanReachAnyOutcome(PaymentStatus outcome) {
        PaymentTransitions.transition(payment, PaymentStatus.VALIDATING, RE_EVALUATE);

        // The bank change is approved, so BENEFICIARY_CHANGE_PENDING is gone;
        // decision 2's reason depends on what compliance says about XXXX9931.
        String decision2 = switch (outcome) {
            case BLOCKED -> "decision 2: COMPLIANCE_HIT";
            case DEFERRED -> "decision 2: COMPLIANCE_EVIDENCE_UNAVAILABLE";
            case AUTHORIZED -> "decision 2: all six checks pass";
            default -> throw new IllegalArgumentException("not an outcome: " + outcome);
        };
        PaymentTransitions.transition(payment, outcome, decision2);

        assertThat(payment.getStatus()).isEqualTo(outcome);
    }

    @Test
    @DisplayName("re-evaluating cannot send a blocked payment back to PROPOSED")
    void blockedPaymentCannotGoBackToProposed() {
        assertThatThrownBy(() -> PaymentTransitions.transition(payment, PaymentStatus.PROPOSED, RE_EVALUATE))
                .isInstanceOf(IllegalPaymentTransitionException.class)
                .hasMessage("Illegal transition: BLOCKED -> PROPOSED (" + RE_EVALUATE + ")");
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.BLOCKED);
    }
}
