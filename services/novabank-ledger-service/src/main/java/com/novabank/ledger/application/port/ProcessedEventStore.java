package com.novabank.ledger.application.port;

import java.util.UUID;

/** Remembers which messages were handled, so redelivered ones can be skipped. */
public interface ProcessedEventStore {

  /**
   * @return {@code false} if the message was already processed (a duplicate delivery)
   */
  boolean markProcessed(UUID eventId, String eventType);
}
