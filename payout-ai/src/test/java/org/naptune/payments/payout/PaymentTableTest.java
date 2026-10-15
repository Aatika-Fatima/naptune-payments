package org.naptune.payments.payout;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;

// Proves Flyway found and applied V1 against real Postgres: the payment table
// (singular, as in v1) exists with the columns and economic-action key that
// build step 1 relies on.
@SpringBootTest
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class PaymentTableTest {

    @Autowired
    JdbcClient jdbc;

    @Test
    void flywayAppliedV1() {
        List<String> applied = jdbc.sql("""
                SELECT version FROM flyway_schema_history
                WHERE success ORDER BY installed_rank""")
                .query(String.class).list();

        assertThat(applied).containsExactly("1");
    }

    @Test
    void paymentTableExists() {
        Boolean exists = jdbc.sql("SELECT to_regclass('public.payment') IS NOT NULL")
                .query(Boolean.class).single();

        assertThat(exists).isTrue();
    }

    @Test
    void paymentTableHasExpectedColumns() {
        List<String> columns = jdbc.sql("""
                SELECT column_name FROM information_schema.columns
                WHERE table_schema = 'public' AND table_name = 'payment'
                ORDER BY ordinal_position""")
                .query(String.class).list();

        assertThat(columns).containsExactly(
                "id", "legal_entity_id", "supplier_id", "normalized_invoice",
                "obligation_id", "purchase_order_id", "amount_paise", "currency",
                "beneficiary_fingerprint", "decision_status", "status",
                "validating_since", "version", "created_at", "updated_at");
    }

    @Test
    void economicActionKeyIsUnique() {
        List<String> keyColumns = jdbc.sql("""
                SELECT kcu.column_name
                FROM information_schema.table_constraints tc
                JOIN information_schema.key_column_usage kcu
                  ON kcu.constraint_name = tc.constraint_name
                 AND kcu.table_schema = tc.table_schema
                WHERE tc.table_name = 'payment'
                  AND tc.constraint_type = 'UNIQUE'
                  AND tc.constraint_name = 'uq_payment_economic_action'
                ORDER BY kcu.ordinal_position""")
                .query(String.class).list();

        assertThat(keyColumns).containsExactly(
                "legal_entity_id", "supplier_id", "normalized_invoice", "obligation_id");
    }
}
