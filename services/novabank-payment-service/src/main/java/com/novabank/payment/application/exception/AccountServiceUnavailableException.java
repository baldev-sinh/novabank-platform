package com.novabank.payment.application.exception;

/**
 * The account service couldn't confirm the account. The deposit is refused (fail closed): money is
 * never sent to an account whose state is unknown. Nothing is stored, so the same request can be
 * retried with the same Idempotency-Key.
 */
public class AccountServiceUnavailableException extends RuntimeException {

  public AccountServiceUnavailableException(String message, Throwable cause) {
    super(message, cause);
  }
}
