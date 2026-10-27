package org.naptune.payments.payout.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.naptune.payments.payout.policy.Decision;
import org.naptune.payments.payout.policy.PaymentDecision;

// Named ...Record: PaymentDecision is the policy's in-memory result (step 7).
@Entity
@Table(name = "payment_decision")
public class PaymentDecisionRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "payment_id", nullable = false, updatable = false)
    private UUID paymentId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false, length = 16)
    private Decision decision;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "reason_codes", nullable = false, updatable = false)
    private List<String> reasonCodes;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "evidence_ids", nullable = false, updatable = false)
    private List<UUID> evidenceIds;

    @Column(name = "policy_version", nullable = false, updatable = false, length = 32)
    private String policyVersion;

    @Column(name = "valid_until", nullable = false, updatable = false)
    private Instant validUntil;

    // Filled by the column default.
    @Column(name = "decided_at", nullable = false, insertable = false, updatable = false)
    private Instant decidedAt;

    protected PaymentDecisionRecord() {
    }

    public PaymentDecisionRecord(UUID paymentId, Decision decision, List<String> reasonCodes,
                                 List<UUID> evidenceIds, String policyVersion, Instant validUntil) {
        this.paymentId = paymentId;
        this.decision = decision;
        this.reasonCodes = reasonCodes;
        this.evidenceIds = evidenceIds;
        this.policyVersion = policyVersion;
        this.validUntil = validUntil;
    }

    // Reason codes keep the engine's order (blocks, then investigations, then
    // defers, each in check order), the same order the API returns and the
    // DECISION audit row records.
    public static PaymentDecisionRecord of(UUID paymentId, PaymentDecision decision, String policyVersion,
                                           Instant validUntil) {
        return new PaymentDecisionRecord(paymentId, decision.decision(),
                List.copyOf(decision.reasonCodes()), decision.evidenceIds(), policyVersion, validUntil);
    }

    // Getters only: a re-evaluation adds a row, it never edits one.
    public Long getId() {
        return id;
    }

    public UUID getPaymentId() {
        return paymentId;
    }

    public Decision getDecision() {
        return decision;
    }

    public List<String> getReasonCodes() {
        return reasonCodes;
    }

    public List<UUID> getEvidenceIds() {
        return evidenceIds;
    }

    public String getPolicyVersion() {
        return policyVersion;
    }

    public Instant getValidUntil() {
        return validUntil;
    }

    public Instant getDecidedAt() {
        return decidedAt;
    }
}
