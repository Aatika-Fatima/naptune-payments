package org.naptune.payments.payout.domain;

import org.naptune.payments.payout.api.ProposalRequest;

// SHA-256 of the canonical request body. The same Idempotency-Key with a
// different hash is a reused key, not a retry.
public final class RequestHash {

    private RequestHash() {
    }

    // Raw values, not normalised ones: INV-94018 and INV94018 under one key are
    // two different bodies. Each field is length-prefixed, so no value can
    // shift into its neighbour, and null is distinct from "".
    public static String of(ProposalRequest request) {
        StringBuilder canonical = new StringBuilder();
        for (Object field : new Object[] {request.legalEntityId(), request.supplierId(), request.invoiceReference(),
                request.obligationId(), request.purchaseOrderId(), request.amountPaise(), request.currency(),
                request.beneficiaryReference()}) {
            if (field == null) {
                canonical.append("-1:");
            } else {
                String value = field.toString();
                canonical.append(value.length()).append(':').append(value);
            }
        }
        return BeneficiaryFingerprint.sha256Hex(canonical.toString());
    }
}
