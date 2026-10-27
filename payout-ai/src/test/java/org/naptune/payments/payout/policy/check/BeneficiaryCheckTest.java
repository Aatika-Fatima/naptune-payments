package org.naptune.payments.payout.policy.check;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.EnumMap;
import java.util.List;
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

class BeneficiaryCheckTest {

    private static final Instant NOW = Instant.parse("2026-10-22T09:30:00Z");
    private static final String APPROVED = "BANK-A-XXXX7712";
    private static final String PENDING = "BANK-B-XXXX9931";

    private final BeneficiaryCheck check = new BeneficiaryCheck();

    @Test
    void approvedAccountPasses() {
        assertThat(apply(APPROVED, vendor(Freshness.FRESH, Map.of("approvedFingerprint", APPROVED, "pendingFingerprint", PENDING))))
                .isEqualTo(new CheckResult.Pass());
    }

    @Test
    void accountAwaitingSecondApprovalBlocks() {
        assertThat(apply(PENDING, vendor(Freshness.FRESH, Map.of("approvedFingerprint", APPROVED, "pendingFingerprint", PENDING))))
                .isEqualTo(new CheckResult.Block("BENEFICIARY_CHANGE_PENDING"));
    }

    @Test
    void unknownAccountBlocks() {
        assertThat(apply("BANK-C-XXXX0001", vendor(Freshness.FRESH, Map.of("approvedFingerprint", APPROVED))))
                .isEqualTo(new CheckResult.Block("BENEFICIARY_NOT_APPROVED"));
    }

    @Test
    void unavailableVendorMasterDefers() {
        Evidence down = Evidence.unavailable(PaymentCheckType.BENEFICIARY_ACCOUNT, "vendor-master", "ExecutionException");

        assertThat(apply(APPROVED, down)).isEqualTo(new CheckResult.Defer("VENDOR_MASTER_UNAVAILABLE"));
    }

    @Test
    void twoApprovedAccountsNeedInvestigation() {
        Evidence conflicting = vendor(Freshness.CONFLICTING, Map.of("approvedFingerprints", List.of(APPROVED, PENDING)));

        assertThat(apply(APPROVED, conflicting))
                .isEqualTo(new CheckResult.Investigate("BENEFICIARY_ACCOUNT_EVIDENCE_CONFLICTING"));
    }

    private CheckResult apply(String proposedAccount, Evidence vendor) {
        SupplierPayment payment = TestPayments.proposal("SUP-1", "INV94018", 100_00L, proposedAccount);
        Map<PaymentCheckType, Evidence> items = new EnumMap<>(PaymentCheckType.class);
        items.put(PaymentCheckType.BENEFICIARY_ACCOUNT, vendor);
        return check.apply(payment, new EvidenceSnapshot(items, NOW));
    }

    private static Evidence vendor(Freshness freshness, Map<String, Object> value) {
        return new Evidence(UUID.randomUUID(), PaymentCheckType.BENEFICIARY_ACCOUNT, "vendor-master", freshness,
                value, "vm-v41", NOW);
    }
}
