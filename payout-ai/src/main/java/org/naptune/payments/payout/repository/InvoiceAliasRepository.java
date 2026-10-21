package org.naptune.payments.payout.repository;

import org.naptune.payments.payout.entity.InvoiceAlias;
import org.naptune.payments.payout.entity.InvoiceAliasId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.UUID;

public interface InvoiceAliasRepository extends JpaRepository<InvoiceAlias, InvoiceAliasId> {

    @Modifying
    @Query(value = """
            INSERT INTO invoice_alias (payment_id, raw_reference, submitted_by)
            VALUES (:paymentId, :rawReference, :submittedBy)
            ON CONFLICT DO NOTHING
            """, nativeQuery = true)
    int insertIfAbsent(@Param("paymentId")UUID paymentId, @Param("rawReference") String rawReference,
                       @Param("submittedBy")String submittedBy);

    // Derived query: Id is the @EmbeddedId field, PaymentId the record component inside it.
    long countByIdPaymentId(UUID paymentId);
}
