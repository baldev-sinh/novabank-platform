package com.novabank.customer.application.exception;

public class DuplicateCustomerException extends RuntimeException {

  public DuplicateCustomerException(String message) {
    super(message);
  }
}
