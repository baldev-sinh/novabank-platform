package com.novabank.ledger.application.event;

import java.time.Instant;
import java.util.UUID;

/** Nothing was posted for this payment, and nothing ever will be. */
public record LedgerPostingFailedEvent(
    UUID eventId, UUID paymentId, String reason, Instant occurredAt) {

  public static final String TYPE = "LedgerPostingFailed.v1";

  public static LedgerPostingFailedEvent of(UUID paymentId, String reason, Instant occurredAt) {
    return new LedgerPostingFailedEvent(UUID.randomUUID(), paymentId, reason, occurredAt);
  }
}
