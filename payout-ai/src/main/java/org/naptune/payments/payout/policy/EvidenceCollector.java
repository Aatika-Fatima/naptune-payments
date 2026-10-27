package org.naptune.payments.payout.policy;

import java.time.Clock;
import java.time.Duration;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.naptune.payments.payout.entity.SupplierPayment;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

// Calls every source at once on virtual threads under one time limit. Doesn't
// judge content or freshness: that is the adapter's and the policy's job.
@Component
public class EvidenceCollector {
    private final List<EvidenceSource> sources;
    private final Duration timeLimit;
    private final Clock clock;
    private final ExecutorService pool = Executors.newVirtualThreadPerTaskExecutor();

    public EvidenceCollector(List<EvidenceSource> sources,
                             @Value("${payout.evidence.time-limit:2s}") Duration timeLimit,
                             Clock clock) {
        this.sources = List.copyOf(sources);
        this.timeLimit = timeLimit;
        this.clock = clock;
    }

    // Never throws and never returns less than one entry per payment check. A 503, a
    // timeout and a bug in an adapter look the same to the policy engine:
    // there is no evidence.
    public EvidenceSnapshot collect(SupplierPayment payment) {
        Map<PaymentCheckType, Evidence> items = new EnumMap<>(PaymentCheckType.class);
        for (PaymentCheckType check : PaymentCheckType.values()) {
            items.put(check, Evidence.unavailable(check, "none", "NO_SOURCE_REGISTERED"));
        }
        List<Callable<Evidence>> calls = sources.stream()
                .map(source -> (Callable<Evidence>) () -> source.fetch(payment))
                .toList();
        List<Future<Evidence>> results;
        try {
            // Unfinished calls are cancelled when the limit passes.
            results = pool.invokeAll(calls, timeLimit.toMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new EvidenceSnapshot(items, clock.instant());
        }
        for (int i = 0; i < sources.size(); i++) {
            EvidenceSource source = sources.get(i);
            Evidence evidence;
            try {
                evidence = results.get(i).get();
            } catch (CancellationException | ExecutionException e) {
                evidence = Evidence.unavailable(source.paymentCheckType(), source.name(), e.getClass().getSimpleName());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                evidence = Evidence.unavailable(source.paymentCheckType(), source.name(), e.getClass().getSimpleName());
            }
            if (evidence == null || evidence.value() == null) {
                evidence = Evidence.unavailable(source.paymentCheckType(), source.name(), "EMPTY_ANSWER");
            }
            items.put(source.paymentCheckType(), evidence);
        }
        return new EvidenceSnapshot(items, clock.instant());
    }
}
