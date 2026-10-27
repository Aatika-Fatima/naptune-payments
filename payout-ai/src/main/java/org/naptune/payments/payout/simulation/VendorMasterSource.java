package org.naptune.payments.payout.simulation;

import java.time.Clock;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.naptune.payments.payout.entity.SupplierPayment;
import org.naptune.payments.payout.policy.Evidence;
import org.naptune.payments.payout.policy.EvidenceSource;
import org.naptune.payments.payout.policy.Freshness;
import org.naptune.payments.payout.policy.PaymentCheckType;
import org.springframework.stereotype.Component;
/*
*VendorMasterSource retrieves the supplier's approved and pending bank-account fingerprints,
* flags multiple approved accounts as conflicting, and returns versioned evidence.
* A separate policy evaluator must decide whether
* the specific account in the payment request is authorized before the agent can proceed.
 */
// A pending change is a fresh fact, not uncertainty: the check blocks on it.
// Two approved accounts at once is CONFLICTING.
@Component
public class VendorMasterSource implements EvidenceSource {

    private final SimulatedWorld world;
    private final Clock clock;

    public VendorMasterSource(SimulatedWorld world, Clock clock) {
        this.world = world;
        this.clock = clock;
    }

    @Override
    public PaymentCheckType paymentCheckType() {
        return PaymentCheckType.BENEFICIARY_ACCOUNT;
    }

    @Override
    public String name() {
        return "vendor-master";
    }

    @Override
    public Evidence fetch(SupplierPayment payment) {

        // 1. Check whether the vendor master is available.
        world.throwIfFailing(paymentCheckType());

        // 2. Get the supplier's bank account details.
        SimulatedWorld.VendorMaster vendor = world.vendorMaster();

        List<String> approvedAccounts = vendor.approvedAccounts;
        String pendingAccount = vendor.pendingAccount;

        // 3. Prepare the evidence.
        Map<String, Object> accountDetails = new HashMap<>();

        if (approvedAccounts.size() == 1) {
            accountDetails.put(
                    "approvedFingerprint",
                    approvedAccounts.getFirst()
            );
        } else if (approvedAccounts.size() > 1) {
            accountDetails.put(
                    "approvedFingerprints",
                    approvedAccounts
            );
        }

        // 4. Include the pending account only if one exists.
        if (pendingAccount != null) {
            accountDetails.put("pendingFingerprint", pendingAccount);
        }

        // 5. Flag multiple approved accounts as conflicting.
        Freshness evidenceStatus = approvedAccounts.size() > 1
                ? Freshness.CONFLICTING
                : Freshness.FRESH;

        // 6. Return the evidence for the payment workflow.
        return new Evidence(
                UUID.randomUUID(),
                paymentCheckType(),
                name(),
                evidenceStatus,
                Map.copyOf(accountDetails),
                "vm-v" + vendor.version,
                clock.instant()
        );
    }
}
