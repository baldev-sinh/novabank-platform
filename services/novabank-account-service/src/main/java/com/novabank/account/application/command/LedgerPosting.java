package com.novabank.account.application.command;

import com.novabank.platform.money.Money;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * A LedgerTransactionPosted event, as far as this service needs it: the projection uses each
 * entry's sequence and balance, the transaction history (8.1) the rest.
 *
 * @param type {@code DEPOSIT} or {@code TRANSFER}
 * @param reference the payer's reference; may be null
 */
public record LedgerPosting(
    UUID eventId,
    UUID paymentId,
    UUID transactionId,
    String type,
    String reference,
    Instant postedAt,
    List<Entry> entries) {

  public LedgerPosting {
    entries = List.copyOf(entries);
  }

  /** {@code direction} is {@code CREDIT} (money in) or {@code DEBIT} (money out). */
  public record Entry(
      UUID accountId, long sequence, String direction, Money amount, Money balanceAfter) {}
}
