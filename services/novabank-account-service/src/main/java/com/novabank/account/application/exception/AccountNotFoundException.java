package com.novabank.account.application.exception;

/** Also used for other users' accounts, so their existence isn't revealed (404). */
public class AccountNotFoundException extends RuntimeException {

  public AccountNotFoundException() {
    super("Account not found.");
  }
}
