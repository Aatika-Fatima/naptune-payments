package org.naptune.payments.payout.repository;

import org.junit.jupiter.api.Test;
import org.naptune.payments.payout.TestcontainersConfiguration;
import org.naptune.payments.payout.entity.InvoiceAlias;
import org.naptune.payments.payout.entity.InvoiceAliasId;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

// Build step 2 "Done when": the same (payment, reference, caller) inserted
// twice returns 1, then 0, and leaves one row. Each test rolls back.
@SpringBootTest
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
@Transactional
class InvoiceAliasRepositoryTest {

    @Autowired
    InvoiceAliasRepository invoiceAliasRepository;
    @Autowired
    SupplierPaymentRepository payments;

    @Test
    void insertIfAbsentInsertsOnceThenLoadsTheSameRow() {
        // invoice_alias.payment_id references payment(id), so the payment comes first.
        UUID paymentId = insertPayment();

        int insertedRowCount = invoiceAliasRepository.insertIfAbsent(paymentId, "INV-94018", "agent-a");
        int insertRowAgainCount = invoiceAliasRepository.insertIfAbsent(paymentId, "INV-94018", "agent-a");

        assertThat(insertedRowCount).isEqualTo(1);
        assertThat(insertRowAgainCount).isZero();
        assertThat(invoiceAliasRepository.count()).isEqualTo(1);

        InvoiceAlias alias = invoiceAliasRepository
                .findById(new InvoiceAliasId(paymentId, "INV-94018", "agent-a"))
                .orElseThrow();
        assertThat(alias.getId().paymentId()).isEqualTo(paymentId);
        assertThat(alias.getId().rawReference()).isEqualTo("INV-94018");
        assertThat(alias.getId().submittedBy()).isEqualTo("agent-a");
        assertThat(alias.getCreatedAt()).isNotNull();
    }

    @Test
    void secondSpellingOfSameInvoiceAddsARow() {
        UUID paymentId = insertPayment();

        // Agent A types INV-94018, Agent B types INV94018: one payment, two aliases.
        int first = invoiceAliasRepository.insertIfAbsent(paymentId, "INV-94018", "agent-a");
        int second = invoiceAliasRepository.insertIfAbsent(paymentId, "INV94018", "agent-b");

        assertThat(first).isEqualTo(1);
        assertThat(second).isEqualTo(1);
        assertThat(invoiceAliasRepository.countByIdPaymentId(paymentId)).isEqualTo(2);
    }

    private UUID insertPayment() {
        UUID id = UUID.randomUUID();
        payments.insertIfAbsent(id, "LE-IN-01", "SUP-7781", "INV94018", "OBL-1",
                "PO-94018", 48_75_000_00L, "INR", "fp-1");
        return id;
    }
}
