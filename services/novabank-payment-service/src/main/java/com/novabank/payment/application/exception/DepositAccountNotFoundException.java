package com.novabank.payment.application.exception;

public class DepositAccountNotFoundException extends RuntimeException {

  public DepositAccountNotFoundException() {
    super("No account with that id.");
  }
}
