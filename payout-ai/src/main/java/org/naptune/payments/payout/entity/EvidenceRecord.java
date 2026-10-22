package org.naptune.payments.payout.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.naptune.payments.payout.policy.Freshness;
import org.naptune.payments.payout.policy.PaymentCheckType;

@Entity
@Table(name = "evidence_record")
public class EvidenceRecord {

    // The Evidence id, so a decision can cite it.
    @Id
    private UUID id;

    @Column(name = "payment_id", nullable = false, updatable = false)
    private UUID paymentId;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_check_type", nullable = false, updatable = false, length = 32)
    private PaymentCheckType paymentCheckType;

    @Column(nullable = false, updatable = false, length = 40)
    private String source;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, updatable = false)
    private Map<String, Object> value;

    @Column(name = "source_version", updatable = false, length = 64)
    private String sourceVersion;

    @Column(name = "source_time", updatable = false)
    private Instant sourceTime;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false, length = 16)
    private Freshness freshness;

    // Filled by the column default.
    @Column(name = "recorded_at", nullable = false, insertable = false, updatable = false)
    private Instant recordedAt;

    protected EvidenceRecord() {
    }

    public EvidenceRecord(UUID id, UUID paymentId, PaymentCheckType paymentCheckType, String source, Map<String, Object> value,
                          String sourceVersion, Instant sourceTime, Freshness freshness) {
        this.id = id;
        this.paymentId = paymentId;
        this.paymentCheckType = paymentCheckType;
        this.source = source;
        this.value = value;
        this.sourceVersion = sourceVersion;
        this.sourceTime = sourceTime;
        this.freshness = freshness;
    }

    // Getters only: evidence is append-only.
    public UUID getId() {
        return id;
    }

    public UUID getPaymentId() {
        return paymentId;
    }

    public PaymentCheckType getPaymentCheckType() {
        return paymentCheckType;
    }

    public String getSource() {
        return source;
    }

    public Map<String, Object> getValue() {
        return value;
    }

    public String getSourceVersion() {
        return sourceVersion;
    }

    public Instant getSourceTime() {
        return sourceTime;
    }

    public Freshness getFreshness() {
        return freshness;
    }

    public Instant getRecordedAt() {
        return recordedAt;
    }
}
