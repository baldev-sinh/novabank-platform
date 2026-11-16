package com.novabank.account.presentation.rest.exception;

import com.novabank.account.application.exception.AccountNotFoundException;
import com.novabank.account.application.exception.CustomerNotVerifiedException;
import com.novabank.account.application.exception.UnsupportedCurrencyException;
import com.novabank.account.domain.exception.DomainException;
import com.novabank.platform.web.PlatformExceptionHandler;
import jakarta.validation.ConstraintViolationException;
import java.net.URI;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Account-specific RFC 9457 mappings. Validation errors, Idempotency-Key reuse (422), security
 * exceptions and the generic 500 come from {@link PlatformExceptionHandler}.
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends PlatformExceptionHandler {

  /** Stable identifier: the client can tell the user to finish identity verification first. */
  static final URI CUSTOMER_NOT_VERIFIED_TYPE =
      URI.create("urn:novabank:problem:customer-not-verified");

  @ExceptionHandler(CustomerNotVerifiedException.class)
  ProblemDetail handleCustomerNotVerified(CustomerNotVerifiedException ex) {
    ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, ex.getMessage());
    problem.setType(CUSTOMER_NOT_VERIFIED_TYPE);
    return problem;
  }

  @ExceptionHandler(AccountNotFoundException.class)
  ProblemDetail handleAccountNotFound(AccountNotFoundException ex) {
    return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
  }

  @ExceptionHandler({UnsupportedCurrencyException.class, DomainException.class})
  ProblemDetail handleBadRequest(RuntimeException ex) {
    return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
  }

  /** E.g. a malformed Idempotency-Key header. */
  @ExceptionHandler(ConstraintViolationException.class)
  ProblemDetail handleConstraintViolation(ConstraintViolationException ex) {
    return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Request validation failed.");
  }
}
