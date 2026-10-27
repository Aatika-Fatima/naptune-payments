package org.naptune.payments.payout.policy.check;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.naptune.payments.payout.entity.SupplierPayment;
import org.naptune.payments.payout.entity.TestPayments;
import org.naptune.payments.payout.policy.CheckResult;
import org.naptune.payments.payout.policy.Evidence;
import org.naptune.payments.payout.policy.EvidenceSnapshot;
import org.naptune.payments.payout.policy.Freshness;
import org.naptune.payments.payout.policy.PaymentCheckType;

class DuplicateCheckTest {

    private static final Instant NOW = Instant.parse("2026-10-22T09:30:00Z");

    private final DuplicateCheck check = new DuplicateCheck();
    private final SupplierPayment payment = TestPayments.proposal("SUP-1", "INV94018", 100_00L, "XXXX7712");

    @Test
    void noneFoundFromACurrentConsumerPasses() {
        assertThat(apply(duplicates(Freshness.FRESH, "NO_DUPLICATE_FOUND"))).isEqualTo(new CheckResult.Pass());
    }

    @Test
    void duplicateFoundBlocks() {
        assertThat(apply(duplicates(Freshness.FRESH, "DUPLICATE_FOUND")))
                .isEqualTo(new CheckResult.Block("DUPLICATE_FOUND"));
    }

    @Test
    void noneFoundFromALaggingConsumerDefers() {
        assertThat(apply(duplicates(Freshness.STALE, "NO_DUPLICATE_FOUND")))
                .isEqualTo(new CheckResult.Defer("DUPLICATE_CHECK_STALE"));
    }

    @Test
    void staleDefersWhateverTheResultSays() {
        assertThat(apply(duplicates(Freshness.STALE, "DUPLICATE_FOUND")))
                .isEqualTo(new CheckResult.Defer("DUPLICATE_CHECK_STALE"));
    }

    @Test
    void unavailableServiceDefers() {
        Evidence down = Evidence.unavailable(PaymentCheckType.DUPLICATE_PAYMENT, "duplicate-service", "CancellationException");

        assertThat(apply(down)).isEqualTo(new CheckResult.Defer("DUPLICATE_CHECK_UNAVAILABLE"));
    }

    @Test
    void unrecognisedResultDoesNotPass() {
        assertThat(apply(duplicates(Freshness.FRESH, "SOMETHING_NEW")))
                .isEqualTo(new CheckResult.Defer("DUPLICATE_CHECK_UNAVAILABLE"));
    }

    private CheckResult apply(Evidence duplicates) {
        Map<PaymentCheckType, Evidence> items = new EnumMap<>(PaymentCheckType.class);
        items.put(PaymentCheckType.DUPLICATE_PAYMENT, duplicates);
        return check.apply(payment, new EvidenceSnapshot(items, NOW));
    }

    private static Evidence duplicates(Freshness freshness, String result) {
        return new Evidence(UUID.randomUUID(), PaymentCheckType.DUPLICATE_PAYMENT, "duplicate-service", freshness,
                Map.of("result", result, "kafkaConsumerProcessingDelaySeconds", 1020L), "offset-881", NOW);
    }
}
