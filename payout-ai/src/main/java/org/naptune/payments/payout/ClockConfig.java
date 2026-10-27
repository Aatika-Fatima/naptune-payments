package org.naptune.payments.payout;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

// Injected wherever "now" matters (freshness limits, leases), so tests can move
// time with a @Primary mutable clock instead of waiting.
@Configuration(proxyBeanMethods = false)
public class ClockConfig {

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }
}
