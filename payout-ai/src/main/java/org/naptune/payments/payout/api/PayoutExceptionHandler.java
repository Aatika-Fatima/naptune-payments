package org.naptune.payments.payout.api;

import org.naptune.payments.payout.domain.exception.PayoutException.EconomicActionConflictException;
import org.naptune.payments.payout.domain.exception.PayoutException.EvaluationNotPossibleException;
import org.naptune.payments.payout.domain.exception.PayoutException.IdempotencyKeyReusedException;
import org.naptune.payments.payout.domain.exception.PayoutException.InvalidInvoiceReferenceException;
import org.naptune.payments.payout.domain.exception.PayoutException.PaymentNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

// Scoped to this controller, so the agent controller can keep its own handler.
// Extends ResponseEntityExceptionHandler so Spring's own request errors (failed
// validation, a missing header, unreadable JSON) stay 400s and never reach the
// catch-all below.
@RestControllerAdvice(assignableTypes = PayoutController.class)
public class PayoutExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(PayoutExceptionHandler.class);

    @ExceptionHandler(InvalidInvoiceReferenceException.class)
    ProblemDetail badRequest(RuntimeException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, e.getMessage());
    }

    @ExceptionHandler(PaymentNotFoundException.class)
    ProblemDetail notFound(PaymentNotFoundException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, e.getMessage());
    }

    @ExceptionHandler({IdempotencyKeyReusedException.class, EvaluationNotPossibleException.class})
    ProblemDetail conflict(RuntimeException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, e.getMessage());
    }

    @ExceptionHandler(EconomicActionConflictException.class)
    ProblemDetail economicConflict(EconomicActionConflictException e) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT,
                "A payment for this obligation already exists with different terms");
        problem.setProperty("paymentId", e.getCanonicalPaymentId());
        return problem;
    }

    // The database is briefly unreachable or a lock timed out: retryable.
    @ExceptionHandler(TransientDataAccessException.class)
    ResponseEntity<ProblemDetail> unavailable(TransientDataAccessException e) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .header(HttpHeaders.RETRY_AFTER, "5")
                .body(ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE, "Try again shortly"));
    }

    // Anything else. The message never leaves the server.
    @ExceptionHandler(Exception.class)
    ProblemDetail unexpected(Exception e) {
        log.error("Unhandled error in payout API", e);
        return ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR, "Internal error");
    }
}
