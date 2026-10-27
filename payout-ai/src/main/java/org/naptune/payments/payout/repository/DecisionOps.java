package org.naptune.payments.payout.repository;

import static org.naptune.payments.payout.domain.PaymentStatus.AUTHORIZED;
import static org.naptune.payments.payout.domain.PaymentStatus.BLOCKED;
import static org.naptune.payments.payout.domain.PaymentStatus.DEFERRED;
import static org.naptune.payments.payout.domain.PaymentStatus.VALIDATING;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.naptune.payments.payout.api.PaymentView;
import org.naptune.payments.payout.domain.DecisionStatus;
import org.naptune.payments.payout.domain.PaymentStatus;
import org.naptune.payments.payout.domain.PaymentTransitions;
import org.naptune.payments.payout.domain.exception.PayoutException.PaymentNotFoundException;
import org.naptune.payments.payout.entity.AuditRecord;
import org.naptune.payments.payout.entity.EvidenceRecord;
import org.naptune.payments.payout.entity.PaymentDecisionRecord;
import org.naptune.payments.payout.entity.SupplierPayment;
import org.naptune.payments.payout.policy.EvidenceSnapshot;
import org.naptune.payments.payout.policy.PaymentDecision;
import org.naptune.payments.payout.policy.PolicyEngine;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

// Transactions 2 and 3. Evidence collection and the policy run between them,
// with no transaction and no lock open.
@Component
public class DecisionOps {

    static final Duration EVALUATION_LEASE = Duration.ofSeconds(60);
    static final Duration DECISION_TTL = Duration.ofMinutes(5);

    private final SupplierPaymentRepository payments;
    private final EvidenceRecordRepository evidenceRecords;
    private final PaymentDecisionRepository decisions;
    private final AuditRecordRepository audit;
    private final Clock clock;

    public DecisionOps(SupplierPaymentRepository payments, EvidenceRecordRepository evidenceRecords,
                       PaymentDecisionRepository decisions, AuditRecordRepository audit, Clock clock) {
        this.payments = payments;
        this.evidenceRecords = evidenceRecords;
        this.decisions = decisions;
        this.audit = audit;
        this.clock = clock;
    }

    // Transaction 2. Empty means someone else holds the evaluation, or the
    // payment is not in one of the states the caller may claim from.
    @Transactional
    public Optional<SupplierPayment> claimEvaluation(UUID paymentId, String actor, Set<PaymentStatus> claimableFrom) {
        SupplierPayment payment = payments.lockById(paymentId)
                .orElseThrow(() -> new PaymentNotFoundException(paymentId));
        PaymentStatus from = payment.getStatus();
        Instant now = clock.instant();
        // An evaluator that died leaves VALIDATING behind; after the lease
        // anyone may take over. Evaluation only reads, so two are harmless.
        boolean abandoned = from == VALIDATING && payment.getValidatingSince() != null
                && payment.getValidatingSince().isBefore(now.minus(EVALUATION_LEASE));
        if (!abandoned && !claimableFrom.contains(from)) {
            return Optional.empty();
        }
        if (!abandoned) {
            PaymentTransitions.transition(payment, VALIDATING, "evaluation claimed");
        }
        payment.setValidatingSince(now);
        audit.save(AuditRecord.transition(paymentId, actor, from, VALIDATING,
                abandoned ? "evaluation re-claimed after lease" : "evaluation claimed"));
        return Optional.of(payment);
    }

    // Transaction 3. Evidence, decision, state and audit commit together or
    // not at all.
    @Transactional
    public void recordDecision(UUID paymentId, String actor, EvidenceSnapshot evidence, PaymentDecision decision) {
        SupplierPayment payment = payments.lockById(paymentId).orElseThrow();
        PaymentStatus next = switch (decision.decision()) {
            case APPROVE -> AUTHORIZED;
            case BLOCK -> BLOCKED;
            case DEFER, INVESTIGATE -> DEFERRED;
        };
        // Throws if a re-claimer already decided: the row is no longer VALIDATING.
        PaymentTransitions.transition(payment, next, String.join(",", decision.reasonCodes()));
        payment.setDecisionStatus(DecisionStatus.of(decision.decision()));
        payment.setValidatingSince(null);
        evidenceRecords.saveAll(EvidenceRecord.from(paymentId, evidence));
        decisions.save(PaymentDecisionRecord.of(paymentId, decision, PolicyEngine.POLICY_VERSION,
                clock.instant().plus(DECISION_TTL)));
        audit.save(AuditRecord.decision(paymentId, actor, VALIDATING, next, decision));
    }

    @Transactional(readOnly = true)
    public PaymentView view(UUID paymentId) {
        SupplierPayment payment = payments.findById(paymentId)
                .orElseThrow(() -> new PaymentNotFoundException(paymentId));
        return PaymentView.of(payment, decisions.findFirstByPaymentIdOrderByIdDesc(paymentId));
    }
}
