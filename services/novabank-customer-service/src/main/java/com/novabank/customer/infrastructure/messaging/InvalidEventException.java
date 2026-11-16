package com.novabank.customer.infrastructure.messaging;

/** The event can never be processed (bad payload or headers): retrying won't help. */
public class InvalidEventException extends RuntimeException {

  public InvalidEventException(String message, Throwable cause) {
    super(message, cause);
  }
}
