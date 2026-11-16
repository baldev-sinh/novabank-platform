package com.novabank.payment.domain.model;

import com.novabank.payment.domain.exception.DomainException;
import com.novabank.platform.money.Money;
import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Aggregate root and saga state (ADR-002): the payment service owns the payment's status; money
 * moves in the ledger, holds live in the account service.
 *
 * <p>Every {@code on...} method returns {@code false} for a reply that doesn't fit the current
 * state (a duplicate, or one that arrives late). Replies come from two topics, so their order isn't
 * guaranteed: for example BalancesUpdated can arrive before LedgerTransactionPosted, and is
 * accepted, because the account service only sends it after the ledger posted.
 */
public final class Payment {

  public static final int MAX_REFERENCE_LENGTH = 140;

  private final UUID id;
  private final PaymentType type;
  private final UUID requestedBy;
  private final UUID fromAccountId;
  private final String toAccountNumber;
  private UUID toAccountId;
  private final Money amount;
  private final String reference;
  private PaymentStatus status;
  private String failureReason;
  private UUID holdId;
  private UUID ledgerTransactionId;
  private final Instant createdAt;
  private Instant updatedAt;

  @SuppressWarnings("checkstyle:ParameterNumber")
  public Payment(
      UUID id,
      PaymentType type,
      UUID requestedBy,
      UUID fromAccountId,
      String toAccountNumber,
      UUID toAccountId,
      Money amount,
      String reference,
      PaymentStatus status,
      String failureReason,
      UUID holdId,
      UUID ledgerTransactionId,
      Instant createdAt,
      Instant updatedAt) {
    this.id = Objects.requireNonNull(id);
    this.type = Objects.requireNonNull(type);
    this.requestedBy = Objects.requireNonNull(requestedBy);
    this.fromAccountId = fromAccountId;
    this.toAccountNumber = toAccountNumber;
    this.toAccountId = toAccountId;
    this.amount = Objects.requireNonNull(amount);
    this.reference = reference;
    this.status = Objects.requireNonNull(status);
    this.failureReason = failureReason;
    this.holdId = holdId;
    this.ledgerTransactionId = ledgerTransactionId;
    this.createdAt = Objects.requireNonNull(createdAt);
    this.updatedAt = Objects.requireNonNull(updatedAt);
  }

  /** A customer pays from their account to an account number. */
  public static Payment transfer(
      UUID requestedBy,
      UUID fromAccountId,
      String toAccountNumber,
      Money amount,
      String reference,
      Clock clock) {
    Objects.requireNonNull(fromAccountId, "fromAccountId cannot be null");
    Objects.requireNonNull(toAccountNumber, "toAccountNumber cannot be null");
    return create(
        PaymentType.TRANSFER,
        requestedBy,
        fromAccountId,
        toAccountNumber,
        null,
        amount,
        reference,
        clock);
  }

  /** Operations staff record money arriving from outside the bank. */
  public static Payment deposit(
      UUID requestedBy, UUID toAccountId, Money amount, String reference, Clock clock) {
    Objects.requireNonNull(toAccountId, "toAccountId cannot be null");
    return create(
        PaymentType.DEPOSIT, requestedBy, null, null, toAccountId, amount, reference, clock);
  }

  private static Payment create(
      PaymentType type,
      UUID requestedBy,
      UUID from,
      String toNumber,
      UUID toId,
      Money amount,
      String reference,
      Clock clock) {
    Objects.requireNonNull(requestedBy, "requestedBy cannot be null");
    Objects.requireNonNull(amount, "amount cannot be null");
    if (!amount.isPositive()) {
      throw new DomainException("Amount must be greater than zero.");
    }
    String cleaned = reference == null || reference.isBlank() ? null : reference.strip();
    if (cleaned != null && cleaned.length() > MAX_REFERENCE_LENGTH) {
      throw new DomainException(
          "Reference must be at most " + MAX_REFERENCE_LENGTH + " characters.");
    }
    Instant now = clock.instant();
    return new Payment(
        UUID.randomUUID(),
        type,
        requestedBy,
        from,
        toNumber,
        toId,
        amount,
        cleaned,
        PaymentStatus.INITIATED,
        null,
        null,
        null,
        now,
        now);
  }

  public boolean onFundsHeld(UUID holdId, UUID beneficiaryAccountId, Clock clock) {
    if (type != PaymentType.TRANSFER || status != PaymentStatus.INITIATED) {
      return false;
    }
    this.holdId = Objects.requireNonNull(holdId);
    this.toAccountId = Objects.requireNonNull(beneficiaryAccountId);
    return moveTo(PaymentStatus.FUNDS_HELD, clock);
  }

  public boolean onFundsHoldFailed(String reason, Clock clock) {
    if (status != PaymentStatus.INITIATED) {
      return false;
    }
    failureReason = reason;
    return moveTo(PaymentStatus.FAILED, clock);
  }

  public boolean onLedgerPosted(UUID transactionId, Clock clock) {
    if (!awaitingLedger()) {
      return false;
    }
    ledgerTransactionId = transactionId;
    return moveTo(PaymentStatus.LEDGER_POSTED, clock);
  }

  /** A transfer must first release its hold (compensation); a deposit just fails. */
  public boolean onLedgerFailed(String reason, Clock clock) {
    if (!awaitingLedger()) {
      return false;
    }
    failureReason = reason;
    return moveTo(
        type == PaymentType.TRANSFER ? PaymentStatus.COMPENSATING : PaymentStatus.FAILED, clock);
  }

  public boolean onHoldReleased(Clock clock) {
    if (status != PaymentStatus.COMPENSATING) {
      return false;
    }
    return moveTo(PaymentStatus.FAILED, clock);
  }

  /** Balances reflect the posting: done. Also accepted before LedgerTransactionPosted arrives. */
  public boolean onBalancesUpdated(UUID transactionId, Clock clock) {
    if (status != PaymentStatus.LEDGER_POSTED && !awaitingLedger()) {
      return false;
    }
    if (ledgerTransactionId == null) {
      ledgerTransactionId = transactionId;
    }
    return moveTo(PaymentStatus.COMPLETED, clock);
  }

  private boolean awaitingLedger() {
    return type == PaymentType.TRANSFER
        ? status == PaymentStatus.FUNDS_HELD
        : status == PaymentStatus.INITIATED;
  }

  private boolean moveTo(PaymentStatus next, Clock clock) {
    status = next;
    updatedAt = clock.instant();
    return true;
  }

  public boolean isOwnedBy(UUID userId) {
    return requestedBy.equals(userId);
  }

  public UUID id() {
    return id;
  }

  public PaymentType type() {
    return type;
  }

  public UUID requestedBy() {
    return requestedBy;
  }

  public UUID fromAccountId() {
    return fromAccountId;
  }

  public String toAccountNumber() {
    return toAccountNumber;
  }

  public UUID toAccountId() {
    return toAccountId;
  }

  public Money amount() {
    return amount;
  }

  public String reference() {
    return reference;
  }

  public PaymentStatus status() {
    return status;
  }

  public String failureReason() {
    return failureReason;
  }

  public UUID holdId() {
    return holdId;
  }

  public UUID ledgerTransactionId() {
    return ledgerTransactionId;
  }

  public Instant createdAt() {
    return createdAt;
  }

  public Instant updatedAt() {
    return updatedAt;
  }
}
