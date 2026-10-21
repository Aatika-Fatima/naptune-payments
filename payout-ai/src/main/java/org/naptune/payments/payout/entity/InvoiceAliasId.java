package org.naptune.payments.payout.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;
import java.util.UUID;

// invoice_alias's three-column primary key as one value. JPA requires a
// composite id to be public and Serializable; the record supplies equals and
// hashCode over all three columns.
@Embeddable
public record InvoiceAliasId(
        @Column(name = "payment_id") UUID paymentId,
        @Column(name = "raw_reference", length = 64) String rawReference,
        @Column(name = "submitted_by", length = 64) String submittedBy) implements Serializable {
}
