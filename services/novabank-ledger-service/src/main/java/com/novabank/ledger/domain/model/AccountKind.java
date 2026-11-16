package com.novabank.ledger.domain.model;

public enum AccountKind {
  /** Mirrors a customer bank account (same id as in the account service). Never negative. */
  CUSTOMER,
  /** The bank's own accounts, e.g. SETTLEMENT per currency. */
  SYSTEM
}
