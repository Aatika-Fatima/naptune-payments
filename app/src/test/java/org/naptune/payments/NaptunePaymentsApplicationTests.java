package org.naptune.payments;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class NaptunePaymentsApplicationTests {

    // Proves the assembled application starts with payout-ai: component scan,
    // Flyway against real Postgres and ddl-auto: validate. Once payout-ai has
    // its first bean (SupplierPaymentRepository, build step 1), autowire it here.
    @Test
    void contextLoads() {
    }

}
