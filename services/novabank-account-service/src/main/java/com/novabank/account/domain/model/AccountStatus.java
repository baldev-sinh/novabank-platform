package com.novabank.account.domain.model;

/**
 * Lifecycle: PENDING → ACTIVE once the ledger holds the account, ACTIVE ⇄ FROZEN, ACTIVE or FROZEN
 * → CLOSED (final). Transitions are enforced by {@link Account}.
 */
public enum AccountStatus {
  /**
   * Opened here, but the ledger hasn't confirmed its side yet (6.4): no money moves until it does,
   * so a payment can't fail with an account the ledger doesn't know.
   */
  PENDING,
  /** Can send and receive money. */
  ACTIVE,
  /** Temporarily blocked by the bank (e.g. suspected fraud): no money moves. */
  FROZEN,
  /** Permanently closed. */
  CLOSED
}
