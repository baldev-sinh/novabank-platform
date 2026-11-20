package com.novabank.auth.presentation.rest.authentication;

/**
 * The cookie-authenticated endpoints (refresh, logout) need {@code X-Requested-With}: a custom
 * header can't be sent cross-site without a CORS preflight, which only the configured origins pass.
 * SameSite=Strict already keeps the cookie off cross-site requests; this is the second layer.
 */
public class MissingCsrfHeaderException extends RuntimeException {

  public MissingCsrfHeaderException() {
    super("This request needs the X-Requested-With header.");
  }
}
