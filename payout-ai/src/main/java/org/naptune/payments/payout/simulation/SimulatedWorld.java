package org.naptune.payments.payout.simulation;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import org.naptune.payments.payout.domain.BeneficiaryFingerprint;
import org.naptune.payments.payout.policy.PaymentCheckType;
import org.springframework.stereotype.Component;

// What each system of record would answer right now. Six small EvidenceSource
// classes read it; tests change the world, not the sources. It holds only data:
// labelling an answer FRESH, STALE, ... stays in the source.
//
// Tests write from their own thread before the evaluation; the collector's
// executor hand-off orders those writes before the sources read them.
@Component
public class SimulatedWorld {

    public static final String SUPPLIER = "SUP-ORION";
    public static final String INVOICE = "INV-94018";
    public static final long INVOICE_AMOUNT_PAISE = 48_75_000_00L;
    // Account references are what a caller sends; the systems of record, like
    // payment.beneficiary_fingerprint, hold only their fingerprints.
    public static final String APPROVED_ACCOUNT = "BANK-A-XXXX7712";
    public static final String PROPOSED_ACCOUNT = "BANK-B-XXXX9931";
    public static final String APPROVED_FINGERPRINT = BeneficiaryFingerprint.of(APPROVED_ACCOUNT);
    public static final String PROPOSED_FINGERPRINT = BeneficiaryFingerprint.of(PROPOSED_ACCOUNT);

    private final Clock clock;
    private final Set<PaymentCheckType> failing = ConcurrentHashMap.newKeySet();
    private final DelegationRegistry delegation = new DelegationRegistry();
    private final Procurement procurement = new Procurement();
    private final VendorMaster vendorMaster = new VendorMaster();
    private final Compliance compliance;
    private final Duplicates duplicates = new Duplicates();
    private final Ledger ledger = new Ledger();

    public SimulatedWorld(Clock clock) {
        this.clock = clock;
        this.compliance = new Compliance(clock);
        allClear();
    }

    // Every source answers, fresh and favourable, for INV-94018 paid to the
    // approved account. Times are relative to the clock when this is called.
    public SimulatedWorld allClear() {
        Instant now = clock.instant();
        failing.clear();
        delegation.active(true).limitPaise(50_00_000_00L).version("del-7");
        procurement.clear().add(INVOICE, INVOICE_AMOUNT_PAISE, "APPROVED", "inv-3", now.minus(Duration.ofMinutes(11)));
        // 41, so the scenario's pending change reads as vm-v42.
        vendorMaster.approvedAccounts(APPROVED_FINGERPRINT).noPendingChange().version(41);
        compliance.up().reset().screened("scr-2026-10-21", now.minus(Duration.ofDays(1)), SUPPLIER, APPROVED_FINGERPRINT);
        duplicates.caughtUp().noneFound().offset("dup-offset-55120");
        ledger.availablePaise(3_40_00_000_00L).observedAt(now).version("ledger-880");
        return this;
    }

    // The brief's 09:30 evaluation: beneficiary change pending second approval,
    // compliance API returning 503, duplicate consumer 17 minutes behind,
    // funds read 20 s ago.
    public SimulatedWorld scenario() {
        allClear();
        vendorMaster.pendingChange(PROPOSED_FINGERPRINT);
        compliance.down();
        duplicates.behind(Duration.ofMinutes(17));
        ledger.observedAt(clock.instant().minusSeconds(20));
        return this;
    }

    // That source now throws instead of answering, until the next allClear().
    public SimulatedWorld fail(PaymentCheckType paymentCheckType) {
        failing.add(paymentCheckType);
        return this;
    }

    void throwIfFailing(PaymentCheckType paymentCheckType) {
        if (failing.contains(paymentCheckType)) {
            throw new IllegalStateException("Simulated failure of the " + paymentCheckType + " source");
        }
    }

    public DelegationRegistry delegation() {
        return delegation;
    }

    public Procurement procurement() {
        return procurement;
    }

    public VendorMaster vendorMaster() {
        return vendorMaster;
    }

    public Compliance compliance() {
        return compliance;
    }

    public Duplicates duplicates() {
        return duplicates;
    }

    public Ledger ledger() {
        return ledger;
    }

    public static final class DelegationRegistry {
        volatile boolean active;
        volatile long limitPaise;
        volatile String version;

        public DelegationRegistry active(boolean active) {
            this.active = active;
            return this;
        }

        public DelegationRegistry limitPaise(long limitPaise) {
            this.limitPaise = limitPaise;
            return this;
        }

        public DelegationRegistry version(String version) {
            this.version = version;
            return this;
        }
    }

    // Holds raw references, as the procurement systems do. Two records for one
    // normalised reference is the "other system's INV94018 import" case.
    public static final class Procurement {
        record InvoiceRecord(String reference, long amountPaise, String currency, String status, String version,
                             Instant approvedAt) {
        }

        final List<InvoiceRecord> records = new CopyOnWriteArrayList<>();

        public Procurement clear() {
            records.clear();
            return this;
        }

        public Procurement add(String reference, long amountPaise, String status, String version, Instant approvedAt) {
            records.add(new InvoiceRecord(reference, amountPaise, "INR", status, version, approvedAt));
            return this;
        }
    }

    public static final class VendorMaster {
        volatile List<String> approvedAccounts = List.of();
        volatile String pendingAccount;
        volatile int version;

        // More than one approved account is a conflict the source reports.
        public VendorMaster approvedAccounts(String... fingerprints) {
            approvedAccounts = List.of(fingerprints);
            return this;
        }

        public VendorMaster pendingChange(String fingerprint) {
            pendingAccount = fingerprint;
            version++;
            return this;
        }

        public VendorMaster noPendingChange() {
            pendingAccount = null;
            return this;
        }

        // The second approval: the account replaces the approved one and the
        // record gets a new version, which the dispatch guard compares.
        public VendorMaster approve(String fingerprint) {
            approvedAccounts = List.of(fingerprint);
            if (fingerprint.equals(pendingAccount)) {
                pendingAccount = null;
            }
            version++;
            return this;
        }

        public VendorMaster version(int version) {
            this.version = version;
            return this;
        }
    }

    // Screening results by party: supplier ids and account fingerprints alike.
    public static final class Compliance {
        private final Clock clock;
        volatile boolean up;
        final Set<String> screened = ConcurrentHashMap.newKeySet();
        final Set<String> hits = ConcurrentHashMap.newKeySet();
        volatile String version;
        volatile Instant screenedAt;

        Compliance(Clock clock) {
            this.clock = clock;
        }

        // The live API returns 503.
        public Compliance down() {
            up = false;
            return this;
        }

        public Compliance up() {
            up = true;
            return this;
        }

        public Compliance reset() {
            screened.clear();
            hits.clear();
            return this;
        }

        public Compliance screened(String version, Instant screenedAt, String... parties) {
            screened.addAll(List.of(parties));
            this.version = version;
            this.screenedAt = screenedAt;
            return this;
        }

        // A new clearance means the service is answering again.
        public Compliance clear(String... parties) {
            up();
            for (String party : parties) {
                hits.remove(party);
            }
            return screened("scr-" + clock.instant(), clock.instant(), parties);
        }

        public Compliance hit(String party) {
            up();
            screened.add(party);
            hits.add(party);
            return this;
        }
    }

    public static final class Duplicates {
        volatile Duration lag = Duration.ZERO;
        volatile boolean found;
        volatile String offset;

        public Duplicates behind(Duration lag) {
            this.lag = lag;
            return this;
        }

        public Duplicates caughtUp() {
            return behind(Duration.ZERO);
        }

        public Duplicates noneFound() {
            found = false;
            return this;
        }

        public Duplicates found() {
            found = true;
            return this;
        }

        public Duplicates offset(String offset) {
            this.offset = offset;
            return this;
        }
    }

    public static final class Ledger {
        volatile long availablePaise;
        volatile Instant observedAt;
        volatile String version;

        public Ledger availablePaise(long availablePaise) {
            this.availablePaise = availablePaise;
            return this;
        }

        public Ledger observedAt(Instant observedAt) {
            this.observedAt = observedAt;
            return this;
        }

        public Ledger version(String version) {
            this.version = version;
            return this;
        }
    }
}
