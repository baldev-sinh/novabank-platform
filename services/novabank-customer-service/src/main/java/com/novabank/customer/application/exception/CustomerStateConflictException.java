package com.novabank.customer.application.exception;

/** The action isn't possible in the customer's current status (409). */
public class CustomerStateConflictException extends RuntimeException {

  public CustomerStateConflictException(String message) {
    super(message);
  }
}
