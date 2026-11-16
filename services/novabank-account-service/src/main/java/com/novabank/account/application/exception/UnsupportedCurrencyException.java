package com.novabank.account.application.exception;

/** The bank doesn't offer accounts in this currency (400). */
public class UnsupportedCurrencyException extends RuntimeException {

  public UnsupportedCurrencyException(String currency) {
    super("Accounts in " + currency + " are not offered.");
  }
}
