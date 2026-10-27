package org.naptune.payments.payout.policy.check;

import org.naptune.payments.payout.entity.SupplierPayment;
import org.naptune.payments.payout.policy.*;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(2)
public class InvoiceCheck implements PolicyCheck {

    @Override
    public CheckResult apply(SupplierPayment payment, EvidenceSnapshot evidence) {
        Evidence invoice = evidence.get(PaymentCheckType.INVOICE);
        // Two records for one normalised reference that disagree: waiting will
        // not fix it, a person must look.
        if (invoice.freshness() == Freshness.CONFLICTING) {
            return new CheckResult.Investigate("INVOICE_EVIDENCE_CONFLICTING");
        }
        if (!invoice.usable()) {
            return new CheckResult.Defer("PROCUREMENT_EVIDENCE_UNAVAILABLE");
        }
        // A current answer that the invoice does not exist is a fact, not uncertainty.
        if (!Boolean.TRUE.equals(invoice.value().get("found"))) {
            return new CheckResult.Block("INVOICE_NOT_FOUND");
        }
        if (!"APPROVED".equals(invoice.value().get("status"))) {
            return new CheckResult.Block("INVOICE_NOT_APPROVED");
        }
        // Paying the right number in the wrong currency is still the wrong amount.
        Object amount = invoice.value().get("amountPaise");
        if (!(amount instanceof Number n) || n.longValue() != payment.getAmountPaise()
                || !payment.getCurrency().equals(invoice.value().get("currency"))) {
            return new CheckResult.Block("INVOICE_AMOUNT_MISMATCH");
        }
        return new CheckResult.Pass();
    }
}