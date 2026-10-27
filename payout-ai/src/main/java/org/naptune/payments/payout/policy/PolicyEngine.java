package org.naptune.payments.payout.policy;

import org.naptune.payments.payout.entity.SupplierPayment;
import org.springframework.stereotype.Component;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Component
public class PolicyEngine {

    public static final String POLICY_VERSION = "payout-policy-1";

    private final List<PolicyCheck> checksInOrder;   // each check carries @Order(1..6)

    public PolicyEngine(List<PolicyCheck> checksInOrder) {
        this.checksInOrder = checksInOrder;
    }

    public PaymentDecision evaluate(SupplierPayment payment, EvidenceSnapshot evidence) {
        Set<String> block = new LinkedHashSet<>();
        Set<String> investigate = new LinkedHashSet<>();
        Set<String> defer = new LinkedHashSet<>();

        // All checks run: the caller sees every reason, not just the first.
        for (PolicyCheck check : checksInOrder) {
            CheckResult result;
            try {
                result = check.apply(payment, evidence);
            } catch (RuntimeException e) {
                // Fail closed. A broken rule may delay a payment; it may not approve one.
                result = new CheckResult.Defer("POLICY_CHECK_ERROR");
            }
            switch (result) {
                case CheckResult.Block b -> block.add(b.reasonCode());
                case CheckResult.Investigate i -> investigate.add(i.reasonCode());
                case CheckResult.Defer d -> defer.add(d.reasonCode());
                case CheckResult.Pass p -> { }
            }
        }

        Set<String> reasons = new LinkedHashSet<>(block);
        reasons.addAll(investigate);
        reasons.addAll(defer);
        // One overall status: BLOCK > INVESTIGATE > DEFER > APPROVE, carrying every reason.
        Decision decision = !block.isEmpty() ? Decision.BLOCK
                : !investigate.isEmpty() ? Decision.INVESTIGATE
                : !defer.isEmpty() ? Decision.DEFER
                : Decision.APPROVE;
        return new PaymentDecision(decision, reasons, evidence.ids());
    }
}