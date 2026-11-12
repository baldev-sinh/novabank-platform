package com.novabank.customer.application.exception;

public class CustomerNotFoundException extends RuntimeException {

  public CustomerNotFoundException(String message) {
    super(message);
  }
}
