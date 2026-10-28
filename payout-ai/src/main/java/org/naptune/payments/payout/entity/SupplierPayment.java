package org.naptune.payments.payout.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;
import org.naptune.payments.payout.domain.DecisionStatus;
import org.naptune.payments.payout.domain.Origin;
import org.naptune.payments.payout.domain.PaymentStatus;

// Named SupplierPayment, not Payment: it is a payment to a supplier, and the
// table keeps v1's name.
@Entity
@Table(name = "payment")
public class SupplierPayment {

    @Id
    private UUID id;

    @Column(name = "legal_entity_id", nullable = false, updatable = false, length = 32)
    private String legalEntityId;

    @Column(name = "supplier_id", nullable = false, updatable = false, length = 64)
    private String supplierId;

    @Column(name = "normalized_invoice", nullable = false, updatable = false, length = 64)
    private String normalizedInvoice;

    @Column(name = "obligation_id", nullable = false, updatable = false, length = 64)
    private String obligationId;

    // Reporting only. Not part of the economic-action key: one PO, many obligations.
    @Column(name = "purchase_order_id", updatable = false, length = 64)
    private String purchaseOrderId;

    @Column(name = "amount_paise", nullable = false, updatable = false)
    private long amountPaise;

    @Column(nullable = false, updatable = false, length = 3)
    private String currency;

    @Column(name = "beneficiary_fingerprint", nullable = false, updatable = false, length = 64)
    private String beneficiaryFingerprint;

    @Enumerated(EnumType.STRING)
    @Column(name = "decision_status", nullable = false, length = 16)
    private DecisionStatus decisionStatus;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private PaymentStatus status;

    @Column(name = "validating_since")
    private Instant validatingSince;

    // Written by the insert that created the row and never changed: a later
    // alias from the other path does not rewrite who proposed it.
    @Enumerated(EnumType.STRING)
    @Column(name = "proposed_via", nullable = false, updatable = false, length = 16)
    private Origin proposedVia;

    // The proposing principal (agent:agent-a, scheduler). The authority source
    // looks up this principal's delegation (gap G8). Rows from before V2.1 read
    // "unknown".
    @Column(name = "proposed_by", nullable = false, updatable = false, length = 80)
    private String proposedBy;

    @Version
    private Long version;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    // Rows are created by the native insert-or-load in SupplierPaymentRepository, never by new.
    protected SupplierPayment() {
    }

    @PreUpdate
    void touch() {
        updatedAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public String getLegalEntityId() {
        return legalEntityId;
    }

    public String getSupplierId() {
        return supplierId;
    }

    public String getNormalizedInvoice() {
        return normalizedInvoice;
    }

    public String getObligationId() {
        return obligationId;
    }

    public String getPurchaseOrderId() {
        return purchaseOrderId;
    }

    public long getAmountPaise() {
        return amountPaise;
    }

    public String getCurrency() {
        return currency;
    }

    public String getBeneficiaryFingerprint() {
        return beneficiaryFingerprint;
    }

    public DecisionStatus getDecisionStatus() {
        return decisionStatus;
    }

    public PaymentStatus getStatus() {
        return status;
    }

    public Instant getValidatingSince() {
        return validatingSince;
    }

    public Origin getProposedVia() {
        return proposedVia;
    }

    public String getProposedBy() {
        return proposedBy;
    }

    public Long getVersion() {
        return version;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    // Only PaymentTransitions should call this, under the row lock.
    public void setStatus(PaymentStatus status) {
        this.status = status;
    }

    public void setDecisionStatus(DecisionStatus decisionStatus) {
        this.decisionStatus = decisionStatus;
    }

    public void setValidatingSince(Instant validatingSince) {
        this.validatingSince = validatingSince;
    }
}
