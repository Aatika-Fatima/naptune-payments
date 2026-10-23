package org.naptune.payments.payout.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.naptune.payments.payout.domain.PaymentStatus;

@Entity
@Table(name = "audit_record")
public class AuditRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "payment_id", nullable = false, updatable = false)
    private UUID paymentId;

    @Column(nullable = false, updatable = false, length = 64)
    private String actor;

    @Column(nullable = false, updatable = false, length = 48)
    private String action;

    @Column(name = "from_state", updatable = false, length = 32)
    private String fromState;

    @Column(name = "to_state", updatable = false, length = 32)
    private String toState;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, updatable = false)
    private Map<String, Object> detail;

    // Filled by the column default.
    @Column(name = "recorded_at", nullable = false, insertable = false, updatable = false)
    private Instant recordedAt;

    protected AuditRecord() {
    }

    private AuditRecord(UUID paymentId, String actor, String action, String fromState, String toState,
                        Map<String, Object> detail) {
        this.paymentId = paymentId;
        this.actor = actor;
        this.action = action;
        this.fromState = fromState;
        this.toState = toState;
        this.detail = detail;
    }

    // actor is the principal, as on every audit row: the client id on the REST
    // path, agent:<id> on the agent path.
    public static AuditRecord received(UUID paymentId, String actor, String rawReference) {
        return new AuditRecord(paymentId, actor, "RECEIVED", null, PaymentStatus.PROPOSED.name(),
                Map.of("reason", "received", "rawReference", rawReference));
    }

    public static AuditRecord transition(UUID paymentId, String actor, PaymentStatus from, PaymentStatus to,
                                         String reason) {
        return new AuditRecord(paymentId, actor, "TRANSITION", from.name(), to.name(), Map.of("reason", reason));
    }

    // decision(…) is added in step 7, once PaymentDecision exists.

    public String getDetailReason() {
        return (String) detail.get("reason");
    }

    // Getters only: audit rows are never edited.
    public Long getId() {
        return id;
    }

    public UUID getPaymentId() {
        return paymentId;
    }

    public String getActor() {
        return actor;
    }

    public String getAction() {
        return action;
    }

    public String getFromState() {
        return fromState;
    }

    public String getToState() {
        return toState;
    }

    public Map<String, Object> getDetail() {
        return detail;
    }

    public Instant getRecordedAt() {
        return recordedAt;
    }
}
