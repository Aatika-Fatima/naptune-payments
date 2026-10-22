package org.naptune.payments.payout.policy;

// Which of the six payment checks a piece of evidence is for. Defined with the
// table that stores it; the checks themselves (BeneficiaryCheck, FundsCheck, ...)
// implement PolicyCheck in build step 7.
public enum PaymentCheckType {
    APPROVAL_AUTHORITY, INVOICE, BENEFICIARY_ACCOUNT, COMPLIANCE_SCREENING, DUPLICATE_PAYMENT, AVAILABLE_FUNDS
}
