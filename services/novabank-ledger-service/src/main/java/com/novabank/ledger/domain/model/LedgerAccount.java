package com.novabank.ledger.domain.model;

import com.novabank.ledger.domain.exception.PostingRejectedException;
import com.novabank.platform.money.Money;
import java.util.Currency;
import java.util.Objects;
import java.util.UUID;

/**
 * An account in the ledger with its running balance. The balance is a cache of its entries (checked
 * by reconciliation); the entries are the truth.
 */
public final class LedgerAccount {

  private final UUID id;
  private final AccountKind kind;
  private final AccountClass accountClass;
  private final Currency currency;
  private Money balance;
  private long lastSequence;

  public LedgerAccount(
      UUID id,
      AccountKind kind,
      AccountClass accountClass,
      Currency currency,
      Money balance,
      long lastSequence) {
    this.id = Objects.requireNonNull(id, "id cannot be null");
    this.kind = Objects.requireNonNull(kind, "kind cannot be null");
    this.accountClass = Objects.requireNonNull(accountClass, "accountClass cannot be null");
    this.currency = Objects.requireNonNull(currency, "currency cannot be null");
    this.balance = Objects.requireNonNull(balance, "balance cannot be null");
    this.lastSequence = lastSequence;
  }

  /**
   * Applies one posting. A customer account can never go below zero (no overdrafts); the caller
   * holds a row lock, so concurrent postings can't both pass this check.
   */
  public PostedEntry apply(Posting posting) {
    if (!posting.amount().currency().equals(currency)) {
      throw new PostingRejectedException(
          RejectionReason.CURRENCY_MISMATCH,
          "Account "
              + id
              + " is in "
              + currency
              + ", not "
              + posting.amount().currencyCode()
              + ".");
    }
    Money change =
        accountClass.signOf(posting.direction()) > 0 ? posting.amount() : posting.amount().negate();
    Money newBalance = balance.plus(change);
    if (kind == AccountKind.CUSTOMER && newBalance.isNegative()) {
      throw new PostingRejectedException(
          RejectionReason.INSUFFICIENT_FUNDS, "Insufficient funds in account " + id + ".");
    }
    balance = newBalance;
    lastSequence++;
    return new PostedEntry(id, posting.direction(), posting.amount(), balance, lastSequence);
  }

  public UUID id() {
    return id;
  }

  public AccountKind kind() {
    return kind;
  }

  public AccountClass accountClass() {
    return accountClass;
  }

  public Currency currency() {
    return currency;
  }

  public Money balance() {
    return balance;
  }

  public long lastSequence() {
    return lastSequence;
  }
}
