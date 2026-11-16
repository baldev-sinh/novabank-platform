package com.novabank.payment.domain.exception;

/** A business rule of the payment domain was broken. */
public class DomainException extends RuntimeException {

  public DomainException(String message) {
    super(message);
  }
}
