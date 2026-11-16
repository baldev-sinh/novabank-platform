package com.novabank.account.domain.exception;

/** A business rule of the account domain was broken. */
public class DomainException extends RuntimeException {

  public DomainException(String message) {
    super(message);
  }
}
