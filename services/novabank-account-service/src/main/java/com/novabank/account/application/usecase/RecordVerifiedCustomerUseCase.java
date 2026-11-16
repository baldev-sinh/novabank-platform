package com.novabank.account.application.usecase;

import java.time.Instant;
import java.util.UUID;

public interface RecordVerifiedCustomerUseCase {

  /**
   * Handles a CustomerVerified event. Safe to call more than once with the same event id.
   *
   * @return {@code false} if this event was already handled
   */
  boolean record(UUID eventId, UUID customerId, UUID userId, Instant verifiedAt);
}
