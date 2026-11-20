package com.novabank.account.domain.model;

import com.novabank.account.domain.exception.DomainException;
import com.novabank.account.domain.valueobject.AccountId;
import com.novabank.account.domain.valueobject.AccountNumber;
import java.time.Clock;
import java.time.Instant;
import java.util.Currency;
import java.util.Objects;
import java.util.UUID;

/**
 * Aggregate root: a customer's bank account. Owns the lifecycle and status (ADR-007). It is not the
 * financial source of truth: balances come from the ledger (ADR-001) and arrive in 2.10.4.
 */
public final class Account {

  private final AccountId id;
  private final AccountNumber number;
  private final UUID ownerUserId;
  private final UUID customerId;
  private final AccountType type;
  private final Currency currency;
  private AccountStatus status;
  private final Instant openedAt;
  private Instant updatedAt;

  /** Optimistic-lock version; {@code null} until first stored. */
  private final Long version;

  private Account(
      AccountId id,
      AccountNumber number,
      UUID ownerUserId,
      UUID customerId,
      AccountType type,
      Currency currency,
      AccountStatus status,
      Instant openedAt,
      Instant updatedAt,
      Long version) {
    this.id = Objects.requireNonNull(id, "AccountId cannot be null");
    this.number = Objects.requireNonNull(number, "AccountNumber cannot be null");
    this.ownerUserId = Objects.requireNonNull(ownerUserId, "ownerUserId cannot be null");
    this.customerId = Objects.requireNonNull(customerId, "customerId cannot be null");
    this.type = Objects.requireNonNull(type, "AccountType cannot be null");
    this.currency = Objects.requireNonNull(currency, "Currency cannot be null");
    this.status = Objects.requireNonNull(status, "AccountStatus cannot be null");
    this.openedAt = Objects.requireNonNull(openedAt, "openedAt cannot be null");
    this.updatedAt = Objects.requireNonNull(updatedAt, "updatedAt cannot be null");
    this.version = version;
  }

  /** Opens a new ACTIVE account for a KYC-verified customer. */
  public static Account open(
      AccountNumber number,
      UUID ownerUserId,
      UUID customerId,
      AccountType type,
      Currency currency,
      Clock clock) {
    Instant now = Objects.requireNonNull(clock, "Clock cannot be null").instant();
    return new Account(
        AccountId.random(),
        number,
        ownerUserId,
        customerId,
        type,
        currency,
        // ACTIVE only once the ledger has the account (activate()).
        AccountStatus.PENDING,
        now,
        now,
        null);
  }

  /** Rebuilds a stored account without re-applying business rules. */
  public static Account restore(
      AccountId id,
      AccountNumber number,
      UUID ownerUserId,
      UUID customerId,
      AccountType type,
      Currency currency,
      AccountStatus status,
      Instant openedAt,
      Instant updatedAt,
      Long version) {
    return new Account(
        id, number, ownerUserId, customerId, type, currency, status, openedAt, updatedAt, version);
  }

  /** The ledger now holds the account (LedgerAccountOpened): PENDING becomes ACTIVE. */
  public void activate(Clock clock) {
    requireStatus("activate", AccountStatus.PENDING);
    changeStatus(AccountStatus.ACTIVE, clock);
  }

  public boolean isPending() {
    return status == AccountStatus.PENDING;
  }

  /** Bank blocks all money movement: ACTIVE becomes FROZEN. */
  public void freeze(Clock clock) {
    requireStatus("freeze", AccountStatus.ACTIVE);
    changeStatus(AccountStatus.FROZEN, clock);
  }

  /** Bank lifts the block: FROZEN becomes ACTIVE. */
  public void unfreeze(Clock clock) {
    requireStatus("unfreeze", AccountStatus.FROZEN);
    changeStatus(AccountStatus.ACTIVE, clock);
  }

  /**
   * Closes the account for good. The zero-balance check needs the balance projection and is added
   * with it (2.10.4).
   */
  public void close(Clock clock) {
    requireStatus("close", AccountStatus.ACTIVE, AccountStatus.FROZEN);
    changeStatus(AccountStatus.CLOSED, clock);
  }

  public boolean isOwnedBy(UUID userId) {
    return ownerUserId.equals(userId);
  }

  private void requireStatus(String action, AccountStatus... allowed) {
    for (AccountStatus candidate : allowed) {
      if (status == candidate) {
        return;
      }
    }
    throw new DomainException("Cannot " + action + " an account in status " + status + ".");
  }

  private void changeStatus(AccountStatus newStatus, Clock clock) {
    this.status = newStatus;
    this.updatedAt = Objects.requireNonNull(clock, "Clock cannot be null").instant();
  }

  public AccountId id() {
    return id;
  }

  public AccountNumber number() {
    return number;
  }

  public UUID ownerUserId() {
    return ownerUserId;
  }

  public UUID customerId() {
    return customerId;
  }

  public AccountType type() {
    return type;
  }

  public Currency currency() {
    return currency;
  }

  public AccountStatus status() {
    return status;
  }

  public Instant openedAt() {
    return openedAt;
  }

  public Instant updatedAt() {
    return updatedAt;
  }

  public Long version() {
    return version;
  }

  @Override
  public boolean equals(Object other) {
    return this == other || (other instanceof Account account && id.equals(account.id));
  }

  @Override
  public int hashCode() {
    return id.hashCode();
  }

  @Override
  public String toString() {
    return "Account{id="
        + id.value()
        + ", type="
        + type
        + ", currency="
        + currency
        + ", status="
        + status
        + '}';
  }
}
