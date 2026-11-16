package com.novabank.ledger.presentation.rest;

import com.novabank.ledger.application.exception.LedgerAccountNotFoundException;
import com.novabank.platform.web.PlatformExceptionHandler;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler extends PlatformExceptionHandler {

  @ExceptionHandler(LedgerAccountNotFoundException.class)
  ProblemDetail handleNotFound(LedgerAccountNotFoundException ex) {
    return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
  }
}
