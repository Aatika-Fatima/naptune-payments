package org.naptune.payments.payout.repository;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.naptune.payments.payout.entity.SupplierPayment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SupplierPaymentRepository extends JpaRepository<SupplierPayment, UUID> {

    // Insert-or-load, part 1. ON CONFLICT DO NOTHING raises no exception, so
    // the transaction stays usable; a unique-violation would mark it
    // rollback-only. A concurrent inserter makes this statement wait for that
    // transaction to finish, then insert 0 rows.
    @Modifying
    @Query(value = """
            INSERT INTO payment (id, legal_entity_id, supplier_id, normalized_invoice, obligation_id,
                                 purchase_order_id, amount_paise, currency, beneficiary_fingerprint)
            VALUES (:id, :entity, :supplier, :invoice, :obligation,
                    :purchaseOrder, :amountPaise, :currency, :beneficiaryFingerprint)
            ON CONFLICT (legal_entity_id, supplier_id, normalized_invoice, obligation_id) DO NOTHING
            """, nativeQuery = true)
    int insertIfAbsent(@Param("id") UUID id, @Param("entity") String entity, @Param("supplier") String supplier,
                       @Param("invoice") String invoice, @Param("obligation") String obligation,
                       @Param("purchaseOrder") String purchaseOrder,
                       @Param("amountPaise") long amountPaise, @Param("currency") String currency,
                       @Param("beneficiaryFingerprint") String beneficiaryFingerprint);

    // Part 2: whoever created it, everyone locks the same row, one at a time.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select p from SupplierPayment p
            where p.legalEntityId = :entity and p.supplierId = :supplier
              and p.normalizedInvoice = :invoice and p.obligationId = :obligation
            """)
    Optional<SupplierPayment> lockByEconomicAction(@Param("entity") String entity, @Param("supplier") String supplier,
                                                   @Param("invoice") String invoice, @Param("obligation") String obligation);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from SupplierPayment p where p.id = :id")
    Optional<SupplierPayment> lockById(@Param("id") UUID id);
}
