package org.naptune.payments.payout.simulation;

import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.naptune.payments.payout.domain.InvoiceReference;
import org.naptune.payments.payout.entity.SupplierPayment;
import org.naptune.payments.payout.policy.Evidence;
import org.naptune.payments.payout.policy.EvidenceSource;
import org.naptune.payments.payout.policy.Freshness;
import org.naptune.payments.payout.policy.PaymentCheckType;
import org.naptune.payments.payout.simulation.SimulatedWorld.Procurement.InvoiceRecord;
import org.springframework.stereotype.Component;

// Looks the invoice up by its normalised reference, so INV-94018 and the other
// system's INV94018 import both match. Matches that agree on amount, currency
// and status are one fact; matches that disagree are CONFLICTING.
@Component
public class ProcurementSource implements EvidenceSource {

    private final SimulatedWorld world;
    private final Clock clock;

    public ProcurementSource(SimulatedWorld world, Clock clock) {
        this.world = world;
        this.clock = clock;
    }

    @Override
    public PaymentCheckType paymentCheckType() {
        return PaymentCheckType.INVOICE;
    }

    @Override
    public String name() {
        return "procurement";
    }

    @Override
    public Evidence fetch(SupplierPayment payment) {
        world.throwIfFailing(paymentCheckType());
        List<InvoiceRecord> matches = world.procurement().records.stream()
                .filter(r -> InvoiceReference.normalize(r.reference()).equals(payment.getNormalizedInvoice()))
                .toList();
        if (matches.isEmpty()) {
            // A current answer that the invoice does not exist; the check blocks on it.
            return answer(Freshness.FRESH, Map.of("found", false, "reference", payment.getNormalizedInvoice()), null);
        }
        InvoiceRecord first = matches.getFirst();
        boolean agree = matches.stream().allMatch(r -> r.amountPaise() == first.amountPaise()
                && r.currency().equals(first.currency()) && r.status().equals(first.status()));
        if (!agree) {
            return answer(Freshness.CONFLICTING, Map.of("found", true, "records", matches.stream().map(this::value).toList()), null);
        }
        return answer(Freshness.FRESH, value(first), first.version());
    }

    private Map<String, Object> value(InvoiceRecord r) {
        return Map.of("found", true, "reference", r.reference(), "amountPaise", r.amountPaise(),
                "currency", r.currency(), "status", r.status(), "approvedAt", r.approvedAt().toString());
    }

    private Evidence answer(Freshness freshness, Map<String, Object> value, String version) {
        return new Evidence(UUID.randomUUID(), paymentCheckType(), name(), freshness, value, version, clock.instant());
    }
}
