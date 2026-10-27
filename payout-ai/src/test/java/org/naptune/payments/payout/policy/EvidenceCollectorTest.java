package org.naptune.payments.payout.policy;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import org.junit.jupiter.api.Test;
import org.naptune.payments.payout.entity.SupplierPayment;
import org.naptune.payments.payout.entity.TestPayments;

// Plain unit test with stub sources: every way a source can fail to answer
// must come back as UNAVAILABLE, and no check may be missing from the snapshot.
class EvidenceCollectorTest {

    private static final Instant NOW = Instant.parse("2026-10-22T09:30:00Z");
    private static final Duration TIME_LIMIT = Duration.ofMillis(200);

    private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
    private final SupplierPayment payment = TestPayments.proposal("SUP-1", "INV94018", 100_00L, "XXXX7712");

    @Test
    void freshAnswerIsKeptAsReturned() {
        Evidence fresh = fresh(PaymentCheckType.INVOICE, "procurement");
        EvidenceSnapshot snapshot = collect(source(PaymentCheckType.INVOICE, "procurement", p -> fresh));

        assertThat(snapshot.get(PaymentCheckType.INVOICE)).isSameAs(fresh);
        assertThat(snapshot.takenAt()).isEqualTo(NOW);
    }

    @Test
    void everyCheckWithoutASourceIsUnavailableNotMissing() {
        EvidenceSnapshot snapshot = collect();

        assertThat(snapshot.items()).containsOnlyKeys(PaymentCheckType.values());
        assertThat(snapshot.items().values()).allSatisfy(evidence -> {
            assertThat(evidence.freshness()).isEqualTo(Freshness.UNAVAILABLE);
            assertThat(evidence.source()).isEqualTo("none");
            assertThat(evidence.value()).containsEntry("cause", "NO_SOURCE_REGISTERED");
        });
    }

    @Test
    void sourceThatThrowsIsUnavailable() {
        EvidenceSnapshot snapshot = collect(source(PaymentCheckType.COMPLIANCE_SCREENING, "compliance-api", p -> {
            throw new IllegalStateException("503 Service Unavailable");
        }));

        assertUnavailable(snapshot.get(PaymentCheckType.COMPLIANCE_SCREENING), "compliance-api", "ExecutionException");
    }

    @Test
    void sourceSlowerThanTheLimitIsCancelledAndUnavailable() {
        EvidenceSource slow = source(PaymentCheckType.AVAILABLE_FUNDS, "treasury-ledger", p -> {
            try {
                Thread.sleep(Duration.ofSeconds(10));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return fresh(PaymentCheckType.AVAILABLE_FUNDS, "treasury-ledger");
        });
        Evidence fresh = fresh(PaymentCheckType.INVOICE, "procurement");

        long start = System.nanoTime();
        EvidenceSnapshot snapshot = collect(slow, source(PaymentCheckType.INVOICE, "procurement", p -> fresh));
        Duration took = Duration.ofNanos(System.nanoTime() - start);

        assertThat(took).isLessThan(Duration.ofSeconds(5));
        assertUnavailable(snapshot.get(PaymentCheckType.AVAILABLE_FUNDS), "treasury-ledger", "CancellationException");
        // The slow source doesn't cost the others their answers.
        assertThat(snapshot.get(PaymentCheckType.INVOICE)).isSameAs(fresh);
    }

    @Test
    void nullAnswerIsUnavailable() {
        EvidenceSnapshot snapshot = collect(source(PaymentCheckType.BENEFICIARY_ACCOUNT, "vendor-master", p -> null));

        assertUnavailable(snapshot.get(PaymentCheckType.BENEFICIARY_ACCOUNT), "vendor-master", "EMPTY_ANSWER");
    }

    @Test
    void answerWithoutAValueIsUnavailable() {
        EvidenceSnapshot snapshot = collect(source(PaymentCheckType.DUPLICATE_PAYMENT, "duplicate-service",
                p -> new Evidence(UUID.randomUUID(), PaymentCheckType.DUPLICATE_PAYMENT, "duplicate-service",
                        Freshness.FRESH, null, "v1", NOW)));

        assertUnavailable(snapshot.get(PaymentCheckType.DUPLICATE_PAYMENT), "duplicate-service", "EMPTY_ANSWER");
    }

    @Test
    void idsListOneEvidencePerCheck() {
        EvidenceSnapshot snapshot = collect(source(PaymentCheckType.INVOICE, "procurement",
                p -> fresh(PaymentCheckType.INVOICE, "procurement")));

        assertThat(snapshot.ids()).hasSize(PaymentCheckType.values().length).doesNotHaveDuplicates();
    }

    private EvidenceSnapshot collect(EvidenceSource... sources) {
        return new EvidenceCollector(List.of(sources), TIME_LIMIT, clock).collect(payment);
    }

    private static void assertUnavailable(Evidence evidence, String source, String cause) {
        assertThat(evidence.freshness()).isEqualTo(Freshness.UNAVAILABLE);
        assertThat(evidence.usable()).isFalse();
        assertThat(evidence.source()).isEqualTo(source);
        assertThat(evidence.value()).containsEntry("cause", cause);
    }

    private static Evidence fresh(PaymentCheckType check, String source) {
        return new Evidence(UUID.randomUUID(), check, source, Freshness.FRESH, Map.of("ok", true), "v1", NOW);
    }

    private static EvidenceSource source(PaymentCheckType check, String name, Function<SupplierPayment, Evidence> fetch) {
        return new EvidenceSource() {
            @Override
            public PaymentCheckType paymentCheckType() {
                return check;
            }

            @Override
            public String name() {
                return name;
            }

            @Override
            public Evidence fetch(SupplierPayment payment) {
                return fetch.apply(payment);
            }
        };
    }
}
