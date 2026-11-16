package com.novabank.ledger.application.response;

import com.novabank.ledger.application.port.LedgerTransactionStore.StoredEntry;
import java.time.Instant;
import java.util.UUID;

public record LedgerEntryResponse(
    UUID transactionId,
    UUID paymentId,
    Instant postedAt,
    String direction,
    String amount,
    String currency,
    String balanceAfter,
    long sequence) {

  public static LedgerEntryResponse from(StoredEntry stored) {
    return new LedgerEntryResponse(
        stored.transactionId(),
        stored.paymentId(),
        stored.postedAt(),
        stored.entry().direction().name(),
        stored.entry().amount().amountString(),
        stored.entry().amount().currencyCode(),
        stored.entry().balanceAfter().amountString(),
        stored.entry().sequence());
  }
}
