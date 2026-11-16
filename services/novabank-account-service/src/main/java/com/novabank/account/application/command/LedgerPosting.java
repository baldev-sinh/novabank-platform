package com.novabank.account.application.command;

import com.novabank.platform.money.Money;
import java.util.List;
import java.util.UUID;

/** A LedgerTransactionPosted event, as far as the projection needs it. */
public record LedgerPosting(UUID eventId, UUID paymentId, UUID transactionId, List<Entry> entries) {

  public LedgerPosting {
    entries = List.copyOf(entries);
  }

  public record Entry(UUID accountId, long sequence, Money balanceAfter) {}
}
