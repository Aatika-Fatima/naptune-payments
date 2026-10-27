package org.naptune.payments.payout.repository;

import java.util.Optional;
import java.util.UUID;
import org.naptune.payments.payout.api.ProposalRequest;
import org.naptune.payments.payout.domain.BeneficiaryFingerprint;
import org.naptune.payments.payout.domain.InvoiceReference;
import org.naptune.payments.payout.domain.RequestHash;
import org.naptune.payments.payout.domain.exception.PayoutException.EconomicActionConflictException;
import org.naptune.payments.payout.domain.exception.PayoutException.IdempotencyKeyReusedException;
import org.naptune.payments.payout.entity.ApiIdempotency;
import org.naptune.payments.payout.entity.ApiIdempotencyId;
import org.naptune.payments.payout.entity.AuditRecord;
import org.naptune.payments.payout.entity.SupplierPayment;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

// Transaction 1. Classifies a request as a retry, a misused key, a duplicate,
// conflicting terms or a new payment, using two keys the database enforces:
// (client, Idempotency-Key) and the economic action.
@Component
public class PaymentIntakeOps {

    private final SupplierPaymentRepository payments;
    private final InvoiceAliasRepository aliases;
    private final ApiIdempotencyRepository idempotency;
    private final AuditRecordRepository audit;

    public PaymentIntakeOps(SupplierPaymentRepository payments, InvoiceAliasRepository aliases,
                            ApiIdempotencyRepository idempotency, AuditRecordRepository audit) {
        this.payments = payments;
        this.aliases = aliases;
        this.idempotency = idempotency;
        this.audit = audit;
    }

    public record IntakeResult(UUID paymentId, boolean created) {
    }

    @Transactional
    public IntakeResult submit(String clientId, String idempotencyKey, ProposalRequest request) {
        String requestHash = RequestHash.of(request);
        ApiIdempotencyId keyId = new ApiIdempotencyId(clientId, idempotencyKey);
        Optional<ApiIdempotency> seen = idempotency.findById(keyId);
        if (seen.isPresent()) {
            return replay(seen.get(), requestHash);
        }

        String invoice = InvoiceReference.normalize(request.invoiceReference());
        String fingerprint = BeneficiaryFingerprint.of(request.beneficiaryReference());
        UUID candidateId = UUID.randomUUID();
        // Insert, then lock: a losing insert waits for the winner's commit and
        // writes 0 rows; FOR UPDATE then loads the winner's row.
        boolean created = payments.insertIfAbsent(candidateId, request.legalEntityId(), request.supplierId(),
                invoice, request.obligationId(), request.purchaseOrderId(), request.amountPaise(),
                request.currency(), fingerprint) == 1;
        SupplierPayment canonical = payments.lockByEconomicAction(request.legalEntityId(), request.supplierId(),
                invoice, request.obligationId()).orElseThrow();

        // Never silently return an existing payment whose terms differ.
        if (canonical.getAmountPaise() != request.amountPaise()
                || !canonical.getCurrency().equals(request.currency())
                || !canonical.getBeneficiaryFingerprint().equals(fingerprint)) {
            throw new EconomicActionConflictException(canonical.getId());
        }

        aliases.insertIfAbsent(canonical.getId(), request.invoiceReference(), clientId);
        if (idempotency.insertIfAbsent(clientId, idempotencyKey, requestHash, canonical.getId()) == 0) {
            // The same key raced itself and the other request committed first.
            return replay(idempotency.findById(keyId).orElseThrow(), requestHash);
        }
        if (created) {
            audit.save(AuditRecord.received(canonical.getId(), clientId, request.invoiceReference()));
        }
        return new IntakeResult(canonical.getId(), created);
    }

    // Same key, different payload: a conflict, not a retry.
    private IntakeResult replay(ApiIdempotency seen, String requestHash) {
        if (!seen.getRequestHash().equals(requestHash)) {
            throw new IdempotencyKeyReusedException(seen.getId().idempotencyKey());
        }
        return new IntakeResult(seen.getPaymentId(), false);
    }
}
