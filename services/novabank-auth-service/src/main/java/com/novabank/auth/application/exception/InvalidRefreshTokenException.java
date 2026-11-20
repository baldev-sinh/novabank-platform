package com.novabank.auth.application.exception;

/** Unknown, expired, used or revoked: the client must sign in again. One message for all cases. */
public class InvalidRefreshTokenException extends RuntimeException {

  public InvalidRefreshTokenException() {
    super("Your session has ended. Please sign in again.");
  }
}
