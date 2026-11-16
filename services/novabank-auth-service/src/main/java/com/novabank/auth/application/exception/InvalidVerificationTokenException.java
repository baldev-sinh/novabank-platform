package com.novabank.auth.application.exception;

/** Unknown, used or expired verification token. The cases aren't told apart on purpose. */
public class InvalidVerificationTokenException extends RuntimeException {

  public InvalidVerificationTokenException() {
    super("The verification link is invalid or has expired.");
  }
}
