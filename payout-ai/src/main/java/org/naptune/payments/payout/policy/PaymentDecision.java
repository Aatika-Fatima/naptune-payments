package org.naptune.payments.payout.policy;

import java.util.List;
import java.util.Set;
import java.util.UUID;

// Decision is defined in step 1, with DecisionStatus.
public record PaymentDecision(Decision decision, Set<String> reasonCodes, List<UUID> evidenceIds) {

    public boolean executionAllowed() {
        return decision == Decision.APPROVE;
    }
}