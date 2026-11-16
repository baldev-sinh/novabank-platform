package com.novabank.platform.idempotency;

/** The Idempotency-Key was already used for a different request (422). */
public class IdempotencyKeyReusedException extends RuntimeException {

  public IdempotencyKeyReusedException() {
    super("This Idempotency-Key was already used for a different request.");
  }
}
