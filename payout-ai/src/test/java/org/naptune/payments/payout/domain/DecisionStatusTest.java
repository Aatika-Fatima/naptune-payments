package org.naptune.payments.payout.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.naptune.payments.payout.policy.Decision;

// DecisionStatus.of maps each Decision one to one; PENDING is only the value a
// payment holds before its first decision.
class DecisionStatusTest {

    @ParameterizedTest
    @EnumSource(Decision.class)
    void mapsEachDecisionToTheSameNamedStatus(Decision decision) {
        assertThat(DecisionStatus.of(decision).name()).isEqualTo(decision.name());
    }

    @Test
    void investigateIsKeptApartFromDefer() {
        // Both leave the payment DEFERRED; decision_status is what tells
        // "waiting for evidence" from "needs a person".
        assertThat(DecisionStatus.of(Decision.INVESTIGATE)).isEqualTo(DecisionStatus.INVESTIGATE);
        assertThat(DecisionStatus.of(Decision.DEFER)).isEqualTo(DecisionStatus.DEFER);
    }

    @Test
    void noDecisionMapsToPending() {
        assertThat(Arrays.stream(Decision.values()).map(DecisionStatus::of))
                .doesNotContain(DecisionStatus.PENDING);
    }

    @Test
    void everyStatusIsADecisionOrPending() {
        assertThat(DecisionStatus.values()).hasSize(Decision.values().length + 1);
    }

    @ParameterizedTest
    @EnumSource(DecisionStatus.class)
    void fitsTheDecisionStatusColumn(DecisionStatus status) {
        // payment.decision_status is VARCHAR(16).
        assertThat(status.name()).hasSizeLessThanOrEqualTo(16);
    }
}
