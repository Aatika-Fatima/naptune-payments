package org.naptune.payments.payout.domain;

// The path that created a payment: payment.proposed_via. Set by the insert
// that won and never changed, so a later duplicate from the other path does
// not rewrite it.
public enum Origin {
    API, AGENT
}
