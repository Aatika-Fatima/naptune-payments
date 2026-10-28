package org.naptune.payments.payout.claims;

// How a claim compared with the evidence the decision used. It is shown to
// the reviewer and never changes the decision.
public enum ClaimVerdict {
    CONFIRMED, CONTRADICTED, UNVERIFIABLE, NOT_ACCEPTED
}
