package org.naptune.payments.payout.policy.check;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.naptune.payments.payout.entity.SupplierPayment;
import org.naptune.payments.payout.entity.TestPayments;
import org.naptune.payments.payout.policy.CheckResult;
import org.naptune.payments.payout.policy.Evidence;
import org.naptune.payments.payout.policy.EvidenceSnapshot;
import org.naptune.payments.payout.policy.Freshness;
import org.naptune.payments.payout.policy.PaymentCheckType;

class InvoiceCheckTest {

    private static final Instant NOW = Instant.parse("2026-10-22T09:30:00Z");
    private static final long AMOUNT = 4_875_000_00L;

    private final InvoiceCheck check = new InvoiceCheck();
    private final SupplierPayment payment = TestPayments.proposal("SUP-1", "INV94018", AMOUNT, "XXXX7712");

    @Test
    void approvedInvoiceWithSameAmountAndCurrencyPasses() {
        assertThat(apply(invoice(Freshness.FRESH, "APPROVED", AMOUNT, "INR"))).isEqualTo(new CheckResult.Pass());
    }

    @Test
    void unapprovedInvoiceBlocks() {
        assertThat(apply(invoice(Freshness.FRESH, "PENDING", AMOUNT, "INR")))
                .isEqualTo(new CheckResult.Block("INVOICE_NOT_APPROVED"));
    }

    @Test
    void differentAmountBlocks() {
        assertThat(apply(invoice(Freshness.FRESH, "APPROVED", 4_857_000_00L, "INR")))
                .isEqualTo(new CheckResult.Block("INVOICE_AMOUNT_MISMATCH"));
    }

    @Test
    void amountInRupeesInsteadOfPaiseBlocks() {
        SupplierPayment inRupees = TestPayments.proposal("SUP-1", "INV94018", 4_875_000L, "XXXX7712");
        Map<PaymentCheckType, Evidence> items = new EnumMap<>(PaymentCheckType.class);
        items.put(PaymentCheckType.INVOICE, invoice(Freshness.FRESH, "APPROVED", AMOUNT, "INR"));

        assertThat(check.apply(inRupees, new EvidenceSnapshot(items, NOW)))
                .isEqualTo(new CheckResult.Block("INVOICE_AMOUNT_MISMATCH"));
    }

    @Test
    void differentCurrencyBlocks() {
        assertThat(apply(invoice(Freshness.FRESH, "APPROVED", AMOUNT, "USD")))
                .isEqualTo(new CheckResult.Block("INVOICE_AMOUNT_MISMATCH"));
    }

    @Test
    void invoiceNotFoundBlocks() {
        Evidence notFound = new Evidence(UUID.randomUUID(), PaymentCheckType.INVOICE, "procurement", Freshness.FRESH,
                Map.of("found", false, "reference", "INV94018"), null, NOW);

        assertThat(apply(notFound)).isEqualTo(new CheckResult.Block("INVOICE_NOT_FOUND"));
    }

    @Test
    void conflictingRecordsNeedInvestigation() {
        assertThat(apply(invoice(Freshness.CONFLICTING, "APPROVED", AMOUNT, "INR")))
                .isEqualTo(new CheckResult.Investigate("INVOICE_EVIDENCE_CONFLICTING"));
    }

    @Test
    void unavailableProcurementDefers() {
        Evidence down = Evidence.unavailable(PaymentCheckType.INVOICE, "procurement", "ExecutionException");

        assertThat(apply(down)).isEqualTo(new CheckResult.Defer("PROCUREMENT_EVIDENCE_UNAVAILABLE"));
    }

    @Test
    void staleApprovalDefers() {
        assertThat(apply(invoice(Freshness.STALE, "APPROVED", AMOUNT, "INR")))
                .isEqualTo(new CheckResult.Defer("PROCUREMENT_EVIDENCE_UNAVAILABLE"));
    }

    private CheckResult apply(Evidence invoice) {
        Map<PaymentCheckType, Evidence> items = new EnumMap<>(PaymentCheckType.class);
        items.put(PaymentCheckType.INVOICE, invoice);
        return check.apply(payment, new EvidenceSnapshot(items, NOW));
    }

    private static Evidence invoice(Freshness freshness, String status, long amountPaise, String currency) {
        return new Evidence(UUID.randomUUID(), PaymentCheckType.INVOICE, "procurement", freshness,
                Map.of("found", true, "reference", "INV-94018", "amountPaise", amountPaise, "currency", currency,
                        "status", status, "approvedAt", NOW.minusSeconds(660).toString()),
                "inv-3", NOW);
    }
}
