package org.naptune.payments.payout.policy;

import org.naptune.payments.payout.entity.SupplierPayment;

// One read-only system of record. Implemented in payout.simulation against
// SimulatedWorld; in production each would be an HTTP adapter.
public interface EvidenceSource {

    PaymentCheckType paymentCheckType();

    String name();

    // May throw and may be slow. Classifying its own answer as FRESH, STALE,
    // INCOMPLETE or CONFLICTING is the adapter's job: only it knows the
    // source's lag limit and what it covers (PDF §1).
    Evidence fetch(SupplierPayment payment);
}
