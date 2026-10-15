package org.naptune.payments.payout.domain;

import org.naptune.payments.payout.policy.Decision;

public enum DecisionStatus {
    PENDING, APPROVE, BLOCK, DEFER, INVESTIGATE;

    // One value per Decision, plus PENDING. INVESTIGATE is kept apart from DEFER:
    // DEFER waits for evidence, INVESTIGATE needs a person.
    public static DecisionStatus of(Decision decision) {
        return switch (decision) {
            case APPROVE -> APPROVE;
            case BLOCK -> BLOCK;
            case DEFER -> DEFER;
            case INVESTIGATE -> INVESTIGATE;
        };
    }
}
