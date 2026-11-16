package com.novabank.customer.presentation.rest.exception;

import com.novabank.customer.application.exception.CustomerNotFoundException;
import com.novabank.customer.application.exception.CustomerStateConflictException;
import com.novabank.customer.application.exception.DuplicateCustomerException;
import com.novabank.customer.domain.exception.DomainException;
import com.novabank.platform.web.PlatformExceptionHandler;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Customer-specific RFC 9457 mappings. Validation errors, security exceptions and the generic 500
 * come from {@link PlatformExceptionHandler}.
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends PlatformExceptionHandler {

  @ExceptionHandler(DuplicateCustomerException.class)
  ProblemDetail handleDuplicateCustomer(DuplicateCustomerException ex) {
    return ProblemDetail.forStatusAndDetail(
        HttpStatus.CONFLICT, "A customer profile already exists for this user.");
  }

  @ExceptionHandler(CustomerNotFoundException.class)
  ProblemDetail handleCustomerNotFound(CustomerNotFoundException ex) {
    return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
  }

  @ExceptionHandler(CustomerStateConflictException.class)
  ProblemDetail handleStateConflict(CustomerStateConflictException ex) {
    return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
  }

  /** Someone else changed the same customer at the same time; the client can retry. */
  @ExceptionHandler(OptimisticLockingFailureException.class)
  ProblemDetail handleConcurrentUpdate(OptimisticLockingFailureException ex) {
    return ProblemDetail.forStatusAndDetail(
        HttpStatus.CONFLICT, "The customer was changed at the same time. Please retry.");
  }

  @ExceptionHandler(DomainException.class)
  ProblemDetail handleDomainRuleViolation(DomainException ex) {
    return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
  }
}
