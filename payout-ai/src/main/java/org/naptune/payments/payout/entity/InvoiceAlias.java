package org.naptune.payments.payout.entity;

import jakarta.persistence.*;

import java.time.Instant;



@Entity
@Table(name = "invoice_alias")
public class InvoiceAlias {

    @EmbeddedId
    private InvoiceAliasId id;
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    // Rows are written by InvoiceAliasRepository.insertIfAbsent, never by new.
    protected InvoiceAlias() {
    }

    public InvoiceAliasId getId() {
        return id;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
