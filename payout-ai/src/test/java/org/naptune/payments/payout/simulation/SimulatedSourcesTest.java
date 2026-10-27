package org.naptune.payments.payout.simulation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.naptune.payments.payout.simulation.SimulatedWorld.APPROVED_FINGERPRINT;
import static org.naptune.payments.payout.simulation.SimulatedWorld.INVOICE_AMOUNT_PAISE;
import static org.naptune.payments.payout.simulation.SimulatedWorld.PROPOSED_FINGERPRINT;
import static org.naptune.payments.payout.simulation.SimulatedWorld.SUPPLIER;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.naptune.payments.payout.entity.SupplierPayment;
import org.naptune.payments.payout.entity.TestPayments;
import org.naptune.payments.payout.policy.Evidence;
import org.naptune.payments.payout.policy.EvidenceSource;
import org.naptune.payments.payout.policy.Freshness;
import org.naptune.payments.payout.policy.PaymentCheckType;

// Plain unit test: each source against one SimulatedWorld on a fixed clock.
// The labels are the guide's scenario table; the other cases each change one
// fact and check that the source, not the world, picks the new label.
class SimulatedSourcesTest {

    private static final Instant NOW = Instant.parse("2026-10-22T09:30:00Z");

    private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
    private final SimulatedWorld world = new SimulatedWorld(clock);
    private final DelegationRegistrySource delegation = new DelegationRegistrySource(world, clock);
    private final ProcurementSource procurement = new ProcurementSource(world, clock);
    private final VendorMasterSource vendorMaster = new VendorMasterSource(world, clock);
    private final ComplianceSource compliance = new ComplianceSource(world, clock);
    private final DuplicatePaymentEvidenceService duplicates = new DuplicatePaymentEvidenceService(world, clock, Duration.ofMinutes(2));
    private final TreasuryLedgerSource ledger = new TreasuryLedgerSource(world, clock, Duration.ofSeconds(60));
    private final List<EvidenceSource> sources = List.of(delegation, procurement, vendorMaster, compliance, duplicates, ledger);

    // The brief's proposal: INV-94018 to the account from the email.
    private final SupplierPayment toEmailAccount = TestPayments.proposal(SUPPLIER, "INV94018", INVOICE_AMOUNT_PAISE, PROPOSED_FINGERPRINT);
    private final SupplierPayment toApprovedAccount = TestPayments.proposal(SUPPLIER, "INV94018", INVOICE_AMOUNT_PAISE, APPROVED_FINGERPRINT);

    @Test
    void oneSourcePerPaymentCheckWithTheNamesStoredInEvidenceRecord() {
        assertThat(sources)
                .extracting(EvidenceSource::paymentCheckType, EvidenceSource::name)
                .containsExactly(
                        tuple(PaymentCheckType.APPROVAL_AUTHORITY, "delegation-registry"),
                        tuple(PaymentCheckType.INVOICE, "procurement"),
                        tuple(PaymentCheckType.BENEFICIARY_ACCOUNT, "vendor-master"),
                        tuple(PaymentCheckType.COMPLIANCE_SCREENING, "compliance-api"),
                        tuple(PaymentCheckType.DUPLICATE_PAYMENT, "duplicate-service"),
                        tuple(PaymentCheckType.AVAILABLE_FUNDS, "treasury-ledger"));
    }

    @Test
    void allClearIsFreshFromEverySource() {
        assertThat(sources).allSatisfy(source ->
                assertThat(source.fetch(toApprovedAccount).freshness()).as(source.name()).isEqualTo(Freshness.FRESH));
    }

    // The 503 is the one row that is not an answer: the source throws and the
    // collector turns it into UNAVAILABLE.
    @Test
    void scenarioAnswersMatchTheGuidesTable() {
        world.scenario();

        assertThat(delegation.fetch(toEmailAccount).value())
                .containsEntry("active", true).containsEntry("limitPaise", 50_00_000_00L);
        assertThat(procurement.fetch(toEmailAccount))
                .returns(Freshness.FRESH, Evidence::freshness)
                .returns("inv-3", Evidence::sourceVersion);
        assertThat(vendorMaster.fetch(toEmailAccount))
                .returns(Freshness.FRESH, Evidence::freshness)
                .returns("vm-v42", Evidence::sourceVersion)
                .extracting(Evidence::value)
                .satisfies(v -> assertThat(v)
                        .containsEntry("approvedFingerprint", APPROVED_FINGERPRINT)
                        .containsEntry("pendingFingerprint", PROPOSED_FINGERPRINT));
        assertThatThrownBy(() -> compliance.fetch(toEmailAccount)).hasMessage("HTTP_503");
        assertThat(duplicates.fetch(toEmailAccount))
                .returns(Freshness.STALE, Evidence::freshness)
                .returns(NOW.minus(Duration.ofMinutes(17)), Evidence::sourceTime)
                .extracting(Evidence::value)
                .satisfies(v -> assertThat(v)
                        .containsEntry("result", "NO_DUPLICATE_FOUND")
                        .containsEntry("kafkaConsumerProcessingDelaySeconds", 1020L));
        assertThat(ledger.fetch(toEmailAccount))
                .returns(Freshness.FRESH, Evidence::freshness)
                .returns(NOW.minusSeconds(20), Evidence::sourceTime);
    }

    // Yesterday's clearance covered SUP-ORION and XXXX7712, not XXXX9931.
    @Test
    void complianceBackUpIsIncompleteForTheUnscreenedAccount() {
        world.scenario().compliance().up();

        Evidence answer = compliance.fetch(toEmailAccount);

        assertThat(answer.freshness()).isEqualTo(Freshness.INCOMPLETE);
        assertThat(answer.value()).containsEntry("partiesAwaitingScreening", List.of(PROPOSED_FINGERPRINT));
        assertThat(compliance.fetch(toApprovedAccount).freshness()).isEqualTo(Freshness.FRESH);
    }

    @Test
    void screeningTheNewAccountMakesComplianceFresh() {
        world.scenario().compliance().clear(SUPPLIER, PROPOSED_FINGERPRINT);

        assertThat(compliance.fetch(toEmailAccount))
                .returns(Freshness.FRESH, Evidence::freshness)
                .extracting(Evidence::value)
                .satisfies(v -> assertThat(v).containsEntry("result", "SCREENING_PASSED"));
    }

    // A hit is a fact about the account even if the rest is unscreened.
    @Test
    void complianceHitIsFresh() {
        world.compliance().hit(PROPOSED_FINGERPRINT);

        assertThat(compliance.fetch(toEmailAccount))
                .returns(Freshness.FRESH, Evidence::freshness)
                .extracting(Evidence::value)
                .satisfies(v -> assertThat(v).containsEntry("result", "RISK_DETECTED").containsEntry("detectedRisks", List.of(PROPOSED_FINGERPRINT)));
    }

    @Test
    void secondApprovalReplacesTheAccountAndBumpsTheVersion() {
        world.scenario().vendorMaster().approve(PROPOSED_FINGERPRINT);

        Evidence answer = vendorMaster.fetch(toEmailAccount);

        assertThat(answer.sourceVersion()).isEqualTo("vm-v43");
        assertThat(answer.value())
                .containsEntry("approvedFingerprint", PROPOSED_FINGERPRINT)
                .doesNotContainKey("pendingFingerprint");
    }

    @Test
    void twoApprovedAccountsConflict() {
        world.vendorMaster().approvedAccounts(APPROVED_FINGERPRINT, PROPOSED_FINGERPRINT);

        assertThat(vendorMaster.fetch(toApprovedAccount).freshness()).isEqualTo(Freshness.CONFLICTING);
    }

    // The other procurement system's import of the same invoice.
    @Test
    void anAgreeingImportIsFreshAndADisagreeingOneConflicts() {
        world.procurement().add("INV94018", INVOICE_AMOUNT_PAISE, "APPROVED", "imp-1", NOW);
        assertThat(procurement.fetch(toApprovedAccount).freshness()).isEqualTo(Freshness.FRESH);

        world.procurement().add("inv/94018", 49_00_000_00L, "APPROVED", "imp-2", NOW);
        assertThat(procurement.fetch(toApprovedAccount).freshness()).isEqualTo(Freshness.CONFLICTING);
    }

    @Test
    void unknownInvoiceIsAFreshNotFound() {
        SupplierPayment other = TestPayments.proposal(SUPPLIER, "INV094018", INVOICE_AMOUNT_PAISE, APPROVED_FINGERPRINT);

        assertThat(procurement.fetch(other))
                .returns(Freshness.FRESH, Evidence::freshness)
                .extracting(Evidence::value)
                .satisfies(v -> assertThat(v).containsEntry("found", false));
    }

    @Test
    void lagAndAgeLimitsAreInclusive() {
        world.duplicates().behind(Duration.ofMinutes(2));
        world.ledger().observedAt(NOW.minusSeconds(60));
        assertThat(duplicates.fetch(toApprovedAccount).freshness()).isEqualTo(Freshness.FRESH);
        assertThat(ledger.fetch(toApprovedAccount).freshness()).isEqualTo(Freshness.FRESH);

        world.duplicates().behind(Duration.ofSeconds(121));
        world.ledger().observedAt(NOW.minusSeconds(61));
        assertThat(duplicates.fetch(toApprovedAccount).freshness()).isEqualTo(Freshness.STALE);
        assertThat(ledger.fetch(toApprovedAccount).freshness()).isEqualTo(Freshness.STALE);
    }

    @ParameterizedTest
    @EnumSource(PaymentCheckType.class)
    void failThrowsFromThatSourceOnlyUntilAllClear(PaymentCheckType failing) {
        world.fail(failing);

        for (EvidenceSource source : sources) {
            if (source.paymentCheckType() == failing) {
                assertThatThrownBy(() -> source.fetch(toApprovedAccount)).isInstanceOf(IllegalStateException.class);
            } else {
                assertThat(source.fetch(toApprovedAccount).freshness()).isEqualTo(Freshness.FRESH);
            }
        }
        world.allClear();
        assertThat(sources).allSatisfy(source -> assertThat(source.fetch(toApprovedAccount)).isNotNull());
    }
}
