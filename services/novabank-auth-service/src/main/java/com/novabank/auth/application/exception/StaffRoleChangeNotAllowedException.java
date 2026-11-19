package com.novabank.auth.application.exception;

/** The request is valid, but the user's current state doesn't allow the change. */
public class StaffRoleChangeNotAllowedException extends RuntimeException {

  public StaffRoleChangeNotAllowedException(String message) {
    super(message);
  }
}
