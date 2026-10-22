package org.naptune.payments.payout.repository;

import org.naptune.payments.payout.entity.ApiIdempotency;
import org.naptune.payments.payout.entity.ApiIdempotencyId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.UUID;

public interface ApiIdempotencyRepository extends JpaRepository<ApiIdempotency, ApiIdempotencyId> {

    // Returns 0 when the same client and key committed first. The caller
    // re-reads and compares hashes instead of failing on a primary-key error.
    @Modifying
    @Query(value = """
            INSERT INTO api_idempotency (client_id, idempotency_key, request_hash, payment_id)
            VALUES (:clientId, :key, :requestHash, :paymentId)
            ON CONFLICT (client_id, idempotency_key) DO NOTHING
            """, nativeQuery = true)
    int insertIfAbsent(@Param("clientId") String clientId, @Param("key") String key,
                       @Param("requestHash") String requestHash, @Param("paymentId") UUID paymentId);
}