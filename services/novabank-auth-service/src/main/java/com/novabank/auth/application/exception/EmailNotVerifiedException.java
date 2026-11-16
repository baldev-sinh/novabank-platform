package com.novabank.auth.application.exception;

/** Correct password, but the email address isn't verified yet. */
public class EmailNotVerifiedException extends RuntimeException {

  public EmailNotVerifiedException() {
    super("Email address not verified.");
  }
}
