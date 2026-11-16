package com.novabank.ledger.application.exception;

public class LedgerAccountNotFoundException extends RuntimeException {

  public LedgerAccountNotFoundException() {
    super("Ledger account not found.");
  }
}
