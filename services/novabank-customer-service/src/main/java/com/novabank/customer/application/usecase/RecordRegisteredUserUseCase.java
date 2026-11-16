package com.novabank.customer.application.usecase;

import java.time.Instant;
import java.util.UUID;

public interface RecordRegisteredUserUseCase {

  /**
   * Handles a UserRegistered event. Safe to call more than once with the same event id.
   *
   * @return {@code false} if this event was already handled
   */
  boolean record(UUID eventId, UUID userId, String email, Instant registeredAt);
}
