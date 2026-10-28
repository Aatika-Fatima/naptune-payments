package org.naptune.payments.payout.claims;

// Where a claim came from, so a reviewer knows how far to trust it. A
// RETRIEVED_DOCUMENT claim must name its document (ck_claim_document).
public enum ClaimSource {
    OPERATOR, AGENT_STATEMENT, RETRIEVED_DOCUMENT
}
