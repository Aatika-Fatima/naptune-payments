package org.naptune.payments.payout.repository;

import java.util.Optional;
import java.util.UUID;
import org.naptune.payments.payout.entity.PaymentDecisionRecord;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PaymentDecisionRepository extends JpaRepository<PaymentDecisionRecord, Long> {

    // The current decision: the highest id wins (ix_decision_payment).
    Optional<PaymentDecisionRecord> findFirstByPaymentIdOrderByIdDesc(UUID paymentId);

    long countByPaymentId(UUID paymentId);
}
