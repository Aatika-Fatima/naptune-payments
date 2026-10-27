package org.naptune.payments.payout.policy;

import java.time.Instant;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

// The answers one evaluation decided on: exactly one Evidence per payment check.
public record EvidenceSnapshot(Map<PaymentCheckType, Evidence> items, Instant takenAt) {

    public EvidenceSnapshot {
        // Copied into an EnumMap so items iterate in check order, whatever map was passed.
        Map<PaymentCheckType, Evidence> copy = new EnumMap<>(PaymentCheckType.class);
        copy.putAll(items);
        items = Collections.unmodifiableMap(copy);
    }

    public Evidence get(PaymentCheckType check) {
        return items.get(check);
    }

    public List<UUID> ids() {
        return items.values().stream().map(Evidence::id).toList();
    }
}
