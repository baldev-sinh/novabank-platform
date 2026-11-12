package com.novabank.customer.domain.exception;

/** A business rule of the customer domain was violated (mapped to 400 or 409 at the API). */
public class DomainException extends RuntimeException {

  public DomainException(String message) {
    super(message);
  }
}
