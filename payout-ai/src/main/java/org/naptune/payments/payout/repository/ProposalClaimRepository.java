package org.naptune.payments.payout.repository;

import java.util.List;
import java.util.UUID;
import org.naptune.payments.payout.entity.ProposalClaim;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProposalClaimRepository extends JpaRepository<ProposalClaim, Long> {

    // A payment's claims in the order they were made (ix_claim_payment).
    List<ProposalClaim> findByPaymentIdOrderById(UUID paymentId);
}
