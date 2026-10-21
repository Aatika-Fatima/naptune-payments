package org.naptune.payments.payout.domain;

import org.junit.platform.suite.api.SelectClasses;
import org.junit.platform.suite.api.Suite;
import org.junit.platform.suite.api.SuiteDisplayName;

// Every arrow of UC-01's state diagram, one class per arrow. Run this to check
// the whole PaymentTransitions table at once.
@Suite
@SuiteDisplayName("Payment state transitions (UC-01)")
@SelectClasses({
        ClaimTransitionCases.class,
        BlockTransitionCases.class,
        DeferTransitionCases.class,
        AuthorizeTransitionCases.class,
        ReEvaluateTransitionCases.class
})
class PaymentTransitionsTest {
}
