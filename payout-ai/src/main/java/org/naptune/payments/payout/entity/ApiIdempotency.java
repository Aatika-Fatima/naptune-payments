package org.naptune.payments.payout.entity;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "api_idempotency")
public class ApiIdempotency {
    @EmbeddedId
    private ApiIdempotencyId id;

    @Column(name = "request_hash", nullable = false, updatable = false, length = 64)
    private String requestHash;

    @Column(name = "payment_id", nullable = false, updatable = false)
    private UUID paymentId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    // Rows are written by ApiIdempotencyRepository.insertIfAbsent, never by new.
    protected ApiIdempotency() {
    }

    public ApiIdempotencyId getId() {
        return id;
    }

    public String getRequestHash() {
        return requestHash;
    }

    public UUID getPaymentId() {
        return paymentId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
