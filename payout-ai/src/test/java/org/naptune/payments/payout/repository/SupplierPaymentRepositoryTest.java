package org.naptune.payments.payout.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.naptune.payments.payout.TestcontainersConfiguration;
import org.naptune.payments.payout.domain.DecisionStatus;
import org.naptune.payments.payout.domain.PaymentStatus;
import org.naptune.payments.payout.entity.SupplierPayment;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

// Build step 1 "Done when": insertIfAbsent twice with one key returns 1, then 0,
// and leaves one row; lockByEconomicAction finds it. Each test rolls back.
@SpringBootTest
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
@Transactional
class SupplierPaymentRepositoryTest {

    @Autowired
    SupplierPaymentRepository payments;

    @Test
    void insertIfAbsentInsertsOnceThenLoadsTheSameRow() {
        UUID first = UUID.randomUUID();

        int inserted = insert(first, 48_75_000_00L);
        int again = insert(UUID.randomUUID(), 48_75_000_00L);

        assertThat(inserted).isEqualTo(1);
        assertThat(again).isZero();
        assertThat(payments.count()).isEqualTo(1);

        SupplierPayment payment = payments.lockByEconomicAction("LE-IN-01", "SUP-7781", "INV94018", "OBL-1")
                .orElseThrow();
        assertThat(payment.getId()).isEqualTo(first);
        assertThat(payment.getAmountPaise()).isEqualTo(48_75_000_00L);
        assertThat(payment.getPurchaseOrderId()).isEqualTo("PO-94018");
    }

    @Test
    void newRowTakesDatabaseDefaults() {
        UUID id = UUID.randomUUID();
        insert(id, 1_000_00L);

        SupplierPayment payment = payments.lockById(id).orElseThrow();

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PROPOSED);
        assertThat(payment.getDecisionStatus()).isEqualTo(DecisionStatus.PENDING);
        assertThat(payment.getVersion()).isZero();
        assertThat(payment.getValidatingSince()).isNull();
        assertThat(payment.getCreatedAt()).isNotNull();
    }

    @Test
    void secondObligationOfSameInvoiceIsItsOwnPayment() {
        insert(UUID.randomUUID(), 1_000_00L);

        int second = payments.insertIfAbsent(UUID.randomUUID(), "LE-IN-01", "SUP-7781", "INV94018", "OBL-2",
                "PO-94018", 1_000_00L, "INR", "fp-1");

        assertThat(second).isEqualTo(1);
        assertThat(payments.count()).isEqualTo(2);
    }

    @Test
    void statusChangeIsSavedAndBumpsVersion() {
        UUID id = UUID.randomUUID();
        insert(id, 1_000_00L);

        SupplierPayment payment = payments.lockById(id).orElseThrow();
        payment.setStatus(PaymentStatus.VALIDATING);
        payments.flush();

        assertThat(payments.lockById(id).orElseThrow().getVersion()).isEqualTo(1);
    }

    private int insert(UUID id, long amountPaise) {
        return payments.insertIfAbsent(id, "LE-IN-01", "SUP-7781", "INV94018", "OBL-1",
                "PO-94018", amountPaise, "INR", "fp-1");
    }
}
