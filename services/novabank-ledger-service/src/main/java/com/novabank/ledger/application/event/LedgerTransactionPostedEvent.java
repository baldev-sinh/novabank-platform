package com.novabank.ledger.application.event;

import com.novabank.ledger.domain.model.PostedEntry;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Money moved. Published on {@code ledger.events.v1} (key = paymentId). Amounts are decimal strings
 * with a currency (Decision L).
 */
public record LedgerTransactionPostedEvent(
    UUID eventId,
    UUID paymentId,
    UUID transactionId,
    String type,
    Instant postedAt,
    List<Entry> entries) {

  public static final String TYPE = "LedgerTransactionPosted.v1";

  public static LedgerTransactionPostedEvent of(
      UUID paymentId, UUID transactionId, String type, Instant postedAt, List<PostedEntry> posted) {
    return new LedgerTransactionPostedEvent(
        UUID.randomUUID(),
        paymentId,
        transactionId,
        type,
        postedAt,
        posted.stream().map(Entry::of).toList());
  }

  /**
   * @param balanceAfter the account's ledger balance right after this entry
   * @param sequence the account's entry number; a projection applies only higher numbers
   */
  public record Entry(
      UUID accountId,
      String direction,
      String amount,
      String currency,
      String balanceAfter,
      long sequence) {

    static Entry of(PostedEntry entry) {
      return new Entry(
          entry.accountId(),
          entry.direction().name(),
          entry.amount().amountString(),
          entry.amount().currencyCode(),
          entry.balanceAfter().amountString(),
          entry.sequence());
    }
  }
}
