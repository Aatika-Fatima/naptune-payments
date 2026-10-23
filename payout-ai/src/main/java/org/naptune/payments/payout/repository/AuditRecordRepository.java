package org.naptune.payments.payout.repository;

import java.util.List;
import java.util.UUID;
import org.naptune.payments.payout.entity.AuditRecord;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuditRecordRepository extends JpaRepository<AuditRecord, Long> {

    // One payment's history in the order it happened (ix_audit_payment).
    List<AuditRecord> findByPaymentIdOrderById(UUID paymentId);
}
