package org.naptune.payments.payout;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

// The module boots against real Postgres with ddl-auto: validate. Once the
// first entity exists, this is also the check that it agrees with its table.
@SpringBootTest
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class PayoutContextTest {

    @Test
    void contextLoads() {
    }
}
