package com.novabank.account.domain.model;

/**
 * Lifecycle: ACTIVE ⇄ FROZEN, ACTIVE or FROZEN → CLOSED (final). Transitions are enforced by {@link
 * Account}.
 */
public enum AccountStatus {
  /** Can send and receive money. */
  ACTIVE,
  /** Temporarily blocked by the bank (e.g. suspected fraud): no money moves. */
  FROZEN,
  /** Permanently closed. */
  CLOSED
}
