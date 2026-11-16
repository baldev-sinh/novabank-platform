package com.novabank.platform.money;

/** An amount or currency that breaks the money rules; a client error (400), not a bug. */
public class InvalidMoneyException extends IllegalArgumentException {

  public InvalidMoneyException(String message) {
    super(message);
  }
}
