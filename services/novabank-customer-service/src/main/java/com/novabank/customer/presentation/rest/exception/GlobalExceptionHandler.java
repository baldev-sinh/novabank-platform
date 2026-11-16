package com.novabank.customer.presentation.rest.exception;

import com.novabank.customer.application.exception.CustomerNotFoundException;
import com.novabank.customer.application.exception.DuplicateCustomerException;
import com.novabank.customer.domain.exception.DomainException;
import com.novabank.platform.web.PlatformExceptionHandler;
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

  @ExceptionHandler(DomainException.class)
  ProblemDetail handleDomainRuleViolation(DomainException ex) {
    return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
  }
}
