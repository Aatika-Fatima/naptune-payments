package org.naptune.payments.payout.policy;

public sealed interface CheckResult {
    record Pass() implements CheckResult { }
    record Block(String reasonCode) implements CheckResult { }
    record Defer(String reasonCode) implements CheckResult { }
    // Conflicting records: waiting will not fix it, a person must look.
    record Investigate(String reasonCode) implements CheckResult { }
}