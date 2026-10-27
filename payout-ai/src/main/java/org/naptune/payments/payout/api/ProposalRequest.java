package org.naptune.payments.payout.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

// The body of POST /api/payments. The controller (step 9) validates it; the
// service and the intake transaction take it as given.
public record ProposalRequest(
        @NotBlank @Size(max = 32) String legalEntityId,
        @NotBlank @Size(max = 64) String supplierId,
        @NotBlank @Size(max = 64) String invoiceReference,
        @NotBlank @Size(max = 64) String obligationId,
        @Size(max = 64) String purchaseOrderId,              // optional, reporting only
        @NotNull @Positive Long amountPaise,
        @NotBlank @Pattern(regexp = "[A-Z]{3}") String currency,
        @NotBlank @Size(max = 64) String beneficiaryReference
) {
}
