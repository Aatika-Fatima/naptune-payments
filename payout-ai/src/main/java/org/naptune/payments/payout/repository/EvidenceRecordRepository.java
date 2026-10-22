package org.naptune.payments.payout.repository;

import java.util.List;
import java.util.UUID;
import org.naptune.payments.payout.entity.EvidenceRecord;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EvidenceRecordRepository extends JpaRepository<EvidenceRecord, UUID> {

    List<EvidenceRecord> findByPaymentId(UUID paymentId);
}
