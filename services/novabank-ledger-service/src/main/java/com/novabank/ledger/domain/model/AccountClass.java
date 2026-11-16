package com.novabank.ledger.domain.model;

/**
 * Which side of the balance sheet an account is on. It decides whether a debit raises or lowers the
 * balance: an ASSET grows with debits (the bank's settlement cash), a LIABILITY grows with credits
 * (customer money is what the bank owes its customers).
 */
public enum AccountClass {
  ASSET,
  LIABILITY;

  /** {@code +1} if a posting in this direction increases the balance, otherwise {@code -1}. */
  public int signOf(Direction direction) {
    return (this == ASSET) == (direction == Direction.DEBIT) ? 1 : -1;
  }
}
