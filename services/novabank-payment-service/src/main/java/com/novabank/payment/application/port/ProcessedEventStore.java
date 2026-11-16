package com.novabank.payment.application.port;

import java.util.UUID;

/** Remembers which messages were handled, so redelivered ones can be skipped. */
public interface ProcessedEventStore {

  boolean markProcessed(UUID eventId, String eventType);
}
