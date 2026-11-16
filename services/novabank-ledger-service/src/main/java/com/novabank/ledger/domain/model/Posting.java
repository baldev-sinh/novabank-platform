package com.novabank.ledger.domain.model;

import com.novabank.ledger.domain.exception.PostingRejectedException;
import com.novabank.platform.money.Money;
import java.util.Objects;
import java.util.UUID;

/** One line of a transaction: debit or credit an account with a positive amount. */
public record Posting(UUID accountId, Direction direction, Money amount) {

  public Posting {
    Objects.requireNonNull(accountId, "accountId cannot be null");
    Objects.requireNonNull(direction, "direction cannot be null");
    Objects.requireNonNull(amount, "amount cannot be null");
    if (!amount.isPositive()) {
      throw new PostingRejectedException(
          RejectionReason.INVALID_POSTINGS, "Posting amounts must be positive.");
    }
  }
}
