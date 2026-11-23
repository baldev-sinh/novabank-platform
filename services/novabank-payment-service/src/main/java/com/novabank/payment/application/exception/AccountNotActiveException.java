package com.novabank.payment.application.exception;

/** The account exists but can't receive money now: PENDING, FROZEN or CLOSED (7.3). */
public class AccountNotActiveException extends RuntimeException {

  private final String status;

  public AccountNotActiveException(String status) {
    super("The account can't receive money while it is " + status + ".");
    this.status = status;
  }

  public String status() {
    return status;
  }
}
