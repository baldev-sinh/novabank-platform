package com.novabank.ledger.application.usecase;

import java.time.Instant;
import java.util.UUID;

public interface OpenLedgerAccountUseCase {

  /** Handles AccountOpened. Safe to call more than once with the same event id. */
  boolean open(UUID eventId, UUID accountId, String currency, Instant openedAt);
}
