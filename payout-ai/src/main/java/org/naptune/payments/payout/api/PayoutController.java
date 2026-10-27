package org.naptune.payments.payout.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.net.URI;
import java.util.UUID;
import org.naptune.payments.payout.api.ProposalService.ProposalOutcome;
import org.naptune.payments.payout.repository.DecisionOps;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// Takes payout proposals. The URL stays /api/payments. It checks shape and
// maps outcomes to status codes; it contains no rule about payments.
// X-Client-Id stands in for an authenticated principal. In production the
// client identity comes from the caller's credentials, never from a header.
@RestController
@RequestMapping("/api/payments")
public class PayoutController {

    private final ProposalService proposals;
    private final DecisionOps decisions;

    public PayoutController(ProposalService proposals, DecisionOps decisions) {
        this.proposals = proposals;
        this.decisions = decisions;
    }

    @PostMapping
    public ResponseEntity<PaymentView> propose(
            @RequestHeader("X-Client-Id") @NotBlank @Size(max = 64) String clientId,
            @RequestHeader("Idempotency-Key") @NotBlank @Size(max = 80) String idempotencyKey,
            @Valid @RequestBody ProposalRequest request) {
        ProposalOutcome outcome = proposals.propose(clientId, idempotencyKey, request);
        // 201: this request created the payment. 200: it already existed and is
        // decided. 202: it exists and another request is still evaluating it.
        HttpStatus status = outcome.created() ? HttpStatus.CREATED
                : outcome.view().decided() ? HttpStatus.OK
                : HttpStatus.ACCEPTED;
        return ResponseEntity.status(status)
                .location(URI.create("/api/payments/" + outcome.view().paymentId()))
                .body(outcome.view());
    }

    @GetMapping("/{id}")
    public PaymentView get(@PathVariable UUID id) {
        return decisions.view(id);
    }

    @PostMapping("/{id}/evaluate")
    public ResponseEntity<PaymentView> evaluate(@PathVariable UUID id,
                                                @RequestHeader("X-Client-Id") @NotBlank @Size(max = 64) String clientId) {
        PaymentView view = proposals.reevaluate(id, clientId);
        return ResponseEntity.status(view.decided() ? HttpStatus.OK : HttpStatus.ACCEPTED).body(view);
    }
}
