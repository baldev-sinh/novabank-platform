package com.novabank.payment.presentation.rest;

import com.novabank.payment.application.exception.AccountNotActiveException;
import com.novabank.payment.application.exception.AccountServiceUnavailableException;
import com.novabank.payment.application.exception.DepositAccountNotFoundException;
import com.novabank.payment.application.exception.PaymentNotFoundException;
import com.novabank.payment.domain.exception.DomainException;
import com.novabank.platform.web.PlatformExceptionHandler;
import jakarta.validation.ConstraintViolationException;
import java.net.URI;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler extends PlatformExceptionHandler {

  @ExceptionHandler(PaymentNotFoundException.class)
  ProblemDetail handleNotFound(PaymentNotFoundException ex) {
    return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
  }

  /** Stable identifier: the client can tell "not ready yet" from other refusals. */
  static final URI ACCOUNT_NOT_ACTIVE_TYPE = URI.create("urn:novabank:problem:account-not-active");

  @ExceptionHandler(AccountNotActiveException.class)
  ProblemDetail handleAccountNotActive(AccountNotActiveException ex) {
    ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
    problem.setType(ACCOUNT_NOT_ACTIVE_TYPE);
    problem.setProperty("accountStatus", ex.status());
    return problem;
  }

  @ExceptionHandler(DepositAccountNotFoundException.class)
  ProblemDetail handleDepositAccountNotFound(DepositAccountNotFoundException ex) {
    return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
  }

  /** Fail closed: nothing was stored, so the same request may be retried with the same key. */
  @ExceptionHandler(AccountServiceUnavailableException.class)
  ResponseEntity<ProblemDetail> handleAccountServiceUnavailable(
      AccountServiceUnavailableException ex) {
    return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
        .header(HttpHeaders.RETRY_AFTER, "30")
        .body(
            ProblemDetail.forStatusAndDetail(
                HttpStatus.SERVICE_UNAVAILABLE,
                "The account can't be checked right now. Please try again shortly."));
  }

  @ExceptionHandler(DomainException.class)
  ProblemDetail handleDomain(DomainException ex) {
    return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
  }

  @ExceptionHandler(ConstraintViolationException.class)
  ProblemDetail handleConstraintViolation(ConstraintViolationException ex) {
    return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Request validation failed.");
  }
}
