package com.novabank.platform.messaging;

/**
 * The message can never be processed (bad payload or headers): retrying won't help, so the error
 * handler sends it straight to the dead-letter topic.
 */
public class InvalidMessageException extends RuntimeException {

  public InvalidMessageException(String message, Throwable cause) {
    super(message, cause);
  }
}
