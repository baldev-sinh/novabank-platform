package com.novabank.account.application.exception;

/** Only customers who passed KYC can open accounts (403). */
public class CustomerNotVerifiedException extends RuntimeException {

  public CustomerNotVerifiedException() {
    super("Your identity hasn't been verified yet, so you can't open an account.");
  }
}
