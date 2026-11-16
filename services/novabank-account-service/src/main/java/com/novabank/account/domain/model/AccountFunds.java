package com.novabank.account.domain.model;

import com.novabank.account.domain.exception.HoldRejectedException;
import com.novabank.platform.money.Money;
import java.time.Clock;
import java.util.Currency;
import java.util.Objects;
import java.util.UUID;

/**
 * The money side of an account: a projection of the ledger balance (ADR-001, ADR-007) and the
 * active holds on it (ADR-006). Loaded under a row lock, so two holds can't both spend the same
 * money.
 *
 * <p>{@code available = ledgerBalance - held}. Every debit needs a hold first, and the projection
 * captures the hold in the same transaction as it lowers the balance, so money is never counted
 * twice or spent twice.
 */
public final class AccountFunds {

  private final UUID accountId;
  private final UUID ownerUserId;
  private final AccountStatus status;
  private final Currency currency;
  private Money ledgerBalance;
  private Money held;
  private long lastLedgerSequence;

  public AccountFunds(
      UUID accountId,
      UUID ownerUserId,
      AccountStatus status,
      Currency currency,
      Money ledgerBalance,
      Money held,
      long lastLedgerSequence) {
    this.accountId = Objects.requireNonNull(accountId);
    this.ownerUserId = Objects.requireNonNull(ownerUserId);
    this.status = Objects.requireNonNull(status);
    this.currency = Objects.requireNonNull(currency);
    this.ledgerBalance = Objects.requireNonNull(ledgerBalance);
    this.held = Objects.requireNonNull(held);
    this.lastLedgerSequence = lastLedgerSequence;
  }

  public Money available() {
    return ledgerBalance.minus(held);
  }

  /**
   * Reserves money for a payment to {@code beneficiary}.
   *
   * @throws HoldRejectedException with the first rule that fails
   */
  public Hold placeHold(
      UUID paymentId, UUID requestedBy, Beneficiary beneficiary, Money amount, Clock clock) {
    if (!ownerUserId.equals(requestedBy)) {
      throw new HoldRejectedException(HoldRejectionReason.NOT_ACCOUNT_OWNER);
    }
    if (status != AccountStatus.ACTIVE) {
      throw new HoldRejectedException(HoldRejectionReason.ACCOUNT_NOT_ACTIVE);
    }
    if (beneficiary.accountId().equals(accountId)) {
      throw new HoldRejectedException(HoldRejectionReason.SAME_ACCOUNT);
    }
    if (beneficiary.status() != AccountStatus.ACTIVE) {
      throw new HoldRejectedException(HoldRejectionReason.BENEFICIARY_NOT_ACTIVE);
    }
    if (!amount.currency().equals(currency) || !beneficiary.currency().equals(currency)) {
      throw new HoldRejectedException(HoldRejectionReason.CURRENCY_MISMATCH);
    }
    if (available().compareTo(amount) < 0) {
      throw new HoldRejectedException(HoldRejectionReason.INSUFFICIENT_FUNDS);
    }
    held = held.plus(amount);
    return new Hold(
        UUID.randomUUID(),
        paymentId,
        accountId,
        beneficiary.accountId(),
        amount,
        HoldStatus.ACTIVE,
        clock.instant());
  }

  /**
   * Takes the ledger's balance after one of its entries. Older or repeated entries (lower or equal
   * sequence) are ignored, so redelivery and reordering can't roll the balance back.
   *
   * @return {@code true} if the projection moved forward
   */
  public boolean applyLedgerEntry(long sequence, Money balanceAfter) {
    if (sequence <= lastLedgerSequence) {
      return false;
    }
    ledgerBalance = balanceAfter;
    lastLedgerSequence = sequence;
    return true;
  }

  /** The beneficiary side as seen by the hold check. */
  public record Beneficiary(UUID accountId, AccountStatus status, Currency currency) {}

  public UUID accountId() {
    return accountId;
  }

  public Currency currency() {
    return currency;
  }

  public Money ledgerBalance() {
    return ledgerBalance;
  }

  public Money held() {
    return held;
  }

  public long lastLedgerSequence() {
    return lastLedgerSequence;
  }
}
