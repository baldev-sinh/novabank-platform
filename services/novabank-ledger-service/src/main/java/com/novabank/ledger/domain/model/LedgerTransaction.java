package com.novabank.ledger.domain.model;

import com.novabank.ledger.domain.exception.PostingRejectedException;
import com.novabank.platform.money.Money;
import java.time.Clock;
import java.time.Instant;
import java.util.Currency;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Aggregate root: an immutable, balanced set of postings (double-entry bookkeeping). Once posted it
 * is never changed or deleted; a correction is a new transaction (ADR-005).
 */
public final class LedgerTransaction {

  public static final int MAX_REFERENCE_LENGTH = 140;

  private final UUID id;
  private final UUID paymentId;
  private final TransactionType type;
  private final List<Posting> postings;
  private final String reference;
  private final Instant postedAt;

  private LedgerTransaction(
      UUID id,
      UUID paymentId,
      TransactionType type,
      List<Posting> postings,
      String reference,
      Instant postedAt) {
    this.id = id;
    this.paymentId = paymentId;
    this.type = type;
    this.postings = postings;
    this.reference = reference;
    this.postedAt = postedAt;
  }

  /**
   * Checks the double-entry rules: at least two postings, each account at most once, and per
   * currency the debits equal the credits.
   *
   * @param paymentId the business operation this posts; unique, so it can't be posted twice
   * @param reference the payer's reference shown on statements; optional, at most 140 characters
   */
  public static LedgerTransaction create(
      UUID paymentId, TransactionType type, List<Posting> postings, String reference, Clock clock) {
    Objects.requireNonNull(paymentId, "paymentId cannot be null");
    Objects.requireNonNull(type, "type cannot be null");
    Objects.requireNonNull(postings, "postings cannot be null");
    if (reference != null && reference.length() > MAX_REFERENCE_LENGTH) {
      throw invalid("A reference can have at most " + MAX_REFERENCE_LENGTH + " characters.");
    }
    if (postings.size() < 2) {
      throw invalid("A transaction needs at least two postings.");
    }
    Set<UUID> accounts = new HashSet<>();
    Map<Currency, Money> net = new HashMap<>();
    for (Posting posting : postings) {
      if (!accounts.add(posting.accountId())) {
        throw invalid("An account can appear only once in a transaction.");
      }
      Money signed =
          posting.direction() == Direction.DEBIT ? posting.amount() : posting.amount().negate();
      net.merge(posting.amount().currency(), signed, Money::plus);
    }
    if (net.values().stream().anyMatch(sum -> !sum.isZero())) {
      throw invalid("Debits and credits must be equal in every currency.");
    }
    return new LedgerTransaction(
        UUID.randomUUID(), paymentId, type, List.copyOf(postings), reference, clock.instant());
  }

  private static PostingRejectedException invalid(String message) {
    return new PostingRejectedException(RejectionReason.INVALID_POSTINGS, message);
  }

  public UUID id() {
    return id;
  }

  public UUID paymentId() {
    return paymentId;
  }

  public TransactionType type() {
    return type;
  }

  public List<Posting> postings() {
    return postings;
  }

  /** May be null. */
  public String reference() {
    return reference;
  }

  public Instant postedAt() {
    return postedAt;
  }
}
