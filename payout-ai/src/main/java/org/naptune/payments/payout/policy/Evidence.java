package org.naptune.payments.payout.policy;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

// One answer from one system of record. PaymentCheckType and Freshness are
// defined in step 4, with evidence_record.
public record Evidence(UUID id, PaymentCheckType paymentCheckType, String source, Freshness freshness,
                       Map<String, Object> value, String sourceVersion, Instant sourceTime) {

    public static Evidence unavailable(PaymentCheckType paymentCheckType, String source, String cause) {
        return new Evidence(UUID.randomUUID(), paymentCheckType, source, Freshness.UNAVAILABLE,
                Map.of("cause", cause), null, null);
    }

    // Only FRESH lets a check pass or block on the value.
    public boolean usable() {
        return freshness == Freshness.FRESH;
    }
}
