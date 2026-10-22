package org.naptune.payments.payout.policy;

// Set by the source adapter: only it knows the source's lag limit and what it covers.
public enum Freshness { FRESH, STALE, UNAVAILABLE, INCOMPLETE, CONFLICTING }
