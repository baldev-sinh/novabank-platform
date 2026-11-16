package com.novabank.ledger.domain.exception;

/** A business rule of the ledger was broken. */
public class DomainException extends RuntimeException {

  public DomainException(String message) {
    super(message);
  }
}
