package com.novabank.account.domain.model;

import com.novabank.account.domain.exception.DomainException;
import com.novabank.platform.money.Money;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Money reserved on an account for one payment (ADR-006). At most one hold per payment. */
public final class Hold {

  private final UUID id;
  private final UUID paymentId;
  private final UUID accountId;
  private final UUID beneficiaryAccountId;
  private final Money amount;
  private HoldStatus status;
  private final Instant createdAt;

  public Hold(
      UUID id,
      UUID paymentId,
      UUID accountId,
      UUID beneficiaryAccountId,
      Money amount,
      HoldStatus status,
      Instant createdAt) {
    this.id = Objects.requireNonNull(id);
    this.paymentId = Objects.requireNonNull(paymentId);
    this.accountId = Objects.requireNonNull(accountId);
    this.beneficiaryAccountId = Objects.requireNonNull(beneficiaryAccountId);
    this.amount = Objects.requireNonNull(amount);
    this.status = Objects.requireNonNull(status);
    this.createdAt = Objects.requireNonNull(createdAt);
  }

  /** Payment failed: the money is available again. */
  public void release() {
    requireActive();
    status = HoldStatus.RELEASED;
  }

  /** The ledger debited the account: the hold is used up. */
  public void capture() {
    requireActive();
    status = HoldStatus.CAPTURED;
  }

  public boolean isActive() {
    return status == HoldStatus.ACTIVE;
  }

  private void requireActive() {
    if (status != HoldStatus.ACTIVE) {
      throw new DomainException("Hold " + id + " is " + status + ", not ACTIVE.");
    }
  }

  public UUID id() {
    return id;
  }

  public UUID paymentId() {
    return paymentId;
  }

  public UUID accountId() {
    return accountId;
  }

  public UUID beneficiaryAccountId() {
    return beneficiaryAccountId;
  }

  public Money amount() {
    return amount;
  }

  public HoldStatus status() {
    return status;
  }

  public Instant createdAt() {
    return createdAt;
  }
}
