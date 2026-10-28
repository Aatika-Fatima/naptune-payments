package org.naptune.payments.payout.claims;

// What a claim is about. The first six match PaymentCheckType by name, so a
// claim is judged against the evidence of the same check. ROUTE and URGENCY
// are outside UC-01: a claim about them is never accepted.
public enum ClaimSubject {
    APPROVAL_AUTHORITY, INVOICE, BENEFICIARY_ACCOUNT, COMPLIANCE_SCREENING, DUPLICATE_PAYMENT, AVAILABLE_FUNDS,
    ROUTE, URGENCY
}
