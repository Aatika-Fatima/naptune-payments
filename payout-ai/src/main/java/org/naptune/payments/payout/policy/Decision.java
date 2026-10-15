package org.naptune.payments.payout.policy;

// Defined with the payment because DecisionStatus maps from it. The policy
// engine that produces a Decision arrives in build step 7.
public enum Decision { APPROVE, BLOCK, DEFER, INVESTIGATE }
