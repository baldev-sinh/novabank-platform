package com.novabank.customer.application.port.messaging;

import java.util.UUID;

/** Remembers which events were handled, so redelivered events can be skipped. */
public interface ProcessedEventStore {

  /**
   * Records the event as processed, in the current transaction.
   *
   * @return {@code false} if it was already processed (a duplicate delivery)
   */
  boolean markProcessed(UUID eventId, String eventType);
}
