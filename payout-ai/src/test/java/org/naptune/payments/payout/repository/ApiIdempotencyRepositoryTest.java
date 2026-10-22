package org.naptune.payments.payout.repository;

import org.junit.jupiter.api.Test;
import org.naptune.payments.payout.TestcontainersConfiguration;
import org.naptune.payments.payout.entity.ApiIdempotency;
import org.naptune.payments.payout.entity.ApiIdempotencyId;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

// Build step 3 "Done when": the same (client, key) inserted twice returns 1,
// then 0, and findById returns the first row's hash and payment.
//
// The data is the brief's ₹48,75,000 payment to SUP-7781 for INV-94018.
// api_idempotency.payment_id references payment(id), so each test inserts the
// payment first. Each test rolls back.
@SpringBootTest
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
@Transactional
class ApiIdempotencyRepositoryTest {

    @Autowired
    ApiIdempotencyRepository idempotency;
    @Autowired
    SupplierPaymentRepository payments;

    // Use case: the AP system posts the payment with Idempotency-Key: key-1,
    // the response times out, and it retries the identical body. The retry is
    // a replay and must resolve to the same payment, not create a second one.
    @Test
    void insertIfAbsentInsertsOnceThenLoadsTheSameRow() {
        UUID paymentId = insertPayment("OBL-1");

        int inserted = idempotency.insertIfAbsent("client-a", "key-1", "hash-1", paymentId);
        int again = idempotency.insertIfAbsent("client-a", "key-1", "hash-1", paymentId);

        assertThat(inserted).isEqualTo(1);
        assertThat(again).isZero();
        assertThat(idempotency.count()).isEqualTo(1);

        ApiIdempotency row = idempotency.findById(new ApiIdempotencyId("client-a", "key-1")).orElseThrow();
        assertThat(row.getId().clientId()).isEqualTo("client-a");
        assertThat(row.getId().idempotencyKey()).isEqualTo("key-1");
        assertThat(row.getRequestHash()).isEqualTo("hash-1");
        assertThat(row.getPaymentId()).isEqualTo(paymentId);
        assertThat(row.getCreatedAt()).isNotNull();
    }

    // Use case: a client bug reuses key-1 for a different request (OBL-2
    // instead of OBL-1). The second body must not overwrite the first: intake
    // sees hash-1 stored, compares it with hash-2 and answers 409.
    @Test
    void sameKeyWithDifferentHashKeepsTheFirstRequest() {
        UUID first = insertPayment("OBL-1");
        UUID second = insertPayment("OBL-2");
        idempotency.insertIfAbsent("client-a", "key-1", "hash-1", first);

        int conflicting = idempotency.insertIfAbsent("client-a", "key-1", "hash-2", second);

        assertThat(conflicting).isZero();
        ApiIdempotency row = idempotency.findById(new ApiIdempotencyId("client-a", "key-1")).orElseThrow();
        assertThat(row.getRequestHash()).isEqualTo("hash-1");
        assertThat(row.getPaymentId()).isEqualTo(first);
    }

    // Use case: the ERP integration and the payment agent both happen to send
    // key-1. Keys are scoped per authenticated client, so neither request is
    // mistaken for the other's retry.
    @Test
    void sameKeyFromAnotherClientIsItsOwnRow() {
        UUID paymentId = insertPayment("OBL-1");

        int clientA = idempotency.insertIfAbsent("client-a", "key-1", "hash-1", paymentId);
        int clientB = idempotency.insertIfAbsent("client-b", "key-1", "hash-1", paymentId);

        assertThat(clientA).isEqualTo(1);
        assertThat(clientB).isEqualTo(1);
        assertThat(idempotency.count()).isEqualTo(2);
    }

    private UUID insertPayment(String obligationId) {
        UUID id = UUID.randomUUID();
        payments.insertIfAbsent(id, "LE-IN-01", "SUP-7781", "INV94018", obligationId,
                "PO-94018", 48_75_000_00L, "INR", "fp-1");
        return id;
    }
}
