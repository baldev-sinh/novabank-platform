package com.novabank.payment.presentation.rest;

import com.novabank.payment.application.exception.PaymentNotFoundException;
import com.novabank.payment.domain.exception.DomainException;
import com.novabank.platform.web.PlatformExceptionHandler;
import jakarta.validation.ConstraintViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler extends PlatformExceptionHandler {

  @ExceptionHandler(PaymentNotFoundException.class)
  ProblemDetail handleNotFound(PaymentNotFoundException ex) {
    return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
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
