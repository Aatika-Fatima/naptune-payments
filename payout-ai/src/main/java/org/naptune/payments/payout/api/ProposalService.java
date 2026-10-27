package org.naptune.payments.payout.api;

import static org.naptune.payments.payout.domain.PaymentStatus.BLOCKED;
import static org.naptune.payments.payout.domain.PaymentStatus.DEFERRED;
import static org.naptune.payments.payout.domain.PaymentStatus.PROPOSED;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.naptune.payments.payout.domain.PaymentStatus;
import org.naptune.payments.payout.domain.exception.PayoutException.EvaluationNotPossibleException;
import org.naptune.payments.payout.domain.exception.PayoutException.IllegalPaymentTransitionException;
import org.naptune.payments.payout.entity.SupplierPayment;
import org.naptune.payments.payout.policy.EvidenceCollector;
import org.naptune.payments.payout.policy.EvidenceSnapshot;
import org.naptune.payments.payout.policy.PaymentDecision;
import org.naptune.payments.payout.policy.PolicyEngine;
import org.naptune.payments.payout.repository.DecisionOps;
import org.naptune.payments.payout.repository.PaymentIntakeOps;
import org.naptune.payments.payout.repository.PaymentIntakeOps.IntakeResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

// Orders the work: intake, claim, collect, evaluate, record, view. Not
// @Transactional, so nothing it calls shares a transaction by accident.
@Service
public class ProposalService {

    private static final Logger log = LoggerFactory.getLogger(ProposalService.class);

    // A proposal evaluates a payment once. Only an explicit re-evaluation may
    // reopen a refusal, so a retry never changes a recorded decision.
    private static final Set<PaymentStatus> FIRST_EVALUATION = Set.of(PROPOSED);
    private static final Set<PaymentStatus> RE_EVALUATION = Set.of(PROPOSED, BLOCKED, DEFERRED);

    private final PaymentIntakeOps intakeOps;
    private final DecisionOps decisionOps;
    private final EvidenceCollector collector;
    private final PolicyEngine engine;

    public ProposalService(PaymentIntakeOps intakeOps, DecisionOps decisionOps, EvidenceCollector collector,
                           PolicyEngine engine) {
        this.intakeOps = intakeOps;
        this.decisionOps = decisionOps;
        this.collector = collector;
        this.engine = engine;
    }

    public record ProposalOutcome(PaymentView view, boolean created) {
    }

    public ProposalOutcome propose(String clientId, String idempotencyKey, ProposalRequest request) {
        IntakeResult intake = intakeOps.submit(clientId, idempotencyKey, request);        // tx 1
        evaluateIfClaimed(intake.paymentId(), clientId, FIRST_EVALUATION);
        // The row is the answer: this request may not be the one that evaluated.
        return new ProposalOutcome(decisionOps.view(intake.paymentId()), intake.created());
    }

    public PaymentView reevaluate(UUID paymentId, String actor) {
        if (!evaluateIfClaimed(paymentId, actor, RE_EVALUATION)) {
            PaymentView current = decisionOps.view(paymentId);
            if (current.status() != PaymentStatus.VALIDATING) {
                throw new EvaluationNotPossibleException(paymentId, current.status());
            }
            return current;
        }
        return decisionOps.view(paymentId);
    }

    private boolean evaluateIfClaimed(UUID paymentId, String actor, Set<PaymentStatus> claimableFrom) {
        Optional<SupplierPayment> claimed = decisionOps.claimEvaluation(paymentId, actor, claimableFrom);   // tx 2
        if (claimed.isEmpty()) {
            return false;
        }
        // No transaction and no lock from here until recordDecision.
        EvidenceSnapshot evidence = collector.collect(claimed.get());
        PaymentDecision decision = engine.evaluate(claimed.get(), evidence);
        try {
            decisionOps.recordDecision(paymentId, actor, evidence, decision);                // tx 3
        } catch (IllegalPaymentTransitionException e) {
            // We were slower than our lease and someone else decided. Their
            // decision stands; ours is discarded with its transaction.
            log.warn("Evaluation of {} superseded: {}", paymentId, e.getMessage());
        }
        return true;
    }
}
