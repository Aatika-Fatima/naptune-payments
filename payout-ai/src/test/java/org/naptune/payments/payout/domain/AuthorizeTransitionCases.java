package org.naptune.payments.payout.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.naptune.payments.payout.domain.TestPayments.paymentIn;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.naptune.payments.payout.domain.exception.PayoutException.IllegalPaymentTransitionException;

// VALIDATING → AUTHORIZED (all pass). Compliance is back and screens XXXX9931
// CLEAR; all six checks pass on fresh evidence. Decision 3 is valid for
// 5 minutes. Nothing in UC-01 moves the payment on: dispatch is UC-04.
@DisplayName("VALIDATING → AUTHORIZED: compliance is back and all six checks pass")
class AuthorizeTransitionCases {

    private static final String DECISION_3 = "decision 3: all six checks pass";

    private final SupplierPayment payment = paymentIn(PaymentStatus.VALIDATING);

    @Test
    @DisplayName("a validating payment is authorized")
    void validatingPaymentIsAuthorized() {
        PaymentTransitions.transition(payment, PaymentStatus.AUTHORIZED, DECISION_3);

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.AUTHORIZED);
    }

    @Test
    @DisplayName("INV-94018 reaches AUTHORIZED through block, deferral and two re-evaluations")
    void invoiceReachesAuthorizedThroughEveryArrow() {
        SupplierPayment inv94018 = paymentIn(PaymentStatus.PROPOSED);

        PaymentTransitions.transition(inv94018, PaymentStatus.VALIDATING, "claim by agent-a");
        PaymentTransitions.transition(inv94018, PaymentStatus.BLOCKED, "decision 1: BENEFICIARY_CHANGE_PENDING");
        PaymentTransitions.transition(inv94018, PaymentStatus.VALIDATING, "re-evaluate after bank change approved");
        PaymentTransitions.transition(inv94018, PaymentStatus.DEFERRED, "decision 2: COMPLIANCE_EVIDENCE_UNAVAILABLE");
        PaymentTransitions.transition(inv94018, PaymentStatus.VALIDATING, "re-evaluate after compliance CLEAR");
        PaymentTransitions.transition(inv94018, PaymentStatus.AUTHORIZED, DECISION_3);

        assertThat(inv94018.getStatus()).isEqualTo(PaymentStatus.AUTHORIZED);
    }

    @ParameterizedTest(name = "AUTHORIZED cannot move to {0}")
    @EnumSource(PaymentStatus.class)
    @DisplayName("an authorized payment cannot move anywhere in UC-01")
    void authorizedPaymentCannotMoveInUc01(PaymentStatus next) {
        PaymentTransitions.transition(payment, PaymentStatus.AUTHORIZED, DECISION_3);

        assertThatThrownBy(() -> PaymentTransitions.transition(payment, next, "test"))
                .isInstanceOf(IllegalPaymentTransitionException.class)
                .hasMessage("Illegal transition: AUTHORIZED -> " + next + " (test)");
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.AUTHORIZED);
    }
}
