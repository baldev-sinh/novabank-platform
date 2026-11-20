package com.novabank.ledger.application.event;

import java.time.Instant;
import java.util.UUID;

/**
 * The ledger now holds this customer account, so money can be posted to it (6.4). Published on
 * {@code ledger.events.v1} (key = accountId); the account service activates the account on it.
 */
public record LedgerAccountOpenedEvent(
    UUID eventId, UUID accountId, String currency, Instant openedAt) {

  public static final String TYPE = "LedgerAccountOpened.v1";

  public static LedgerAccountOpenedEvent of(UUID accountId, String currency, Instant openedAt) {
    return new LedgerAccountOpenedEvent(UUID.randomUUID(), accountId, currency, openedAt);
  }
}
