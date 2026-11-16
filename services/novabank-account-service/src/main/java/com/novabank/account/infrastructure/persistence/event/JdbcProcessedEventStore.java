package com.novabank.account.infrastructure.persistence.event;

import com.novabank.account.application.port.messaging.ProcessedEventStore;
import com.novabank.platform.messaging.consumer.ProcessedEvents;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

/** Adapter from the application's port to the platform's processed-events registry. */
@Repository
@RequiredArgsConstructor
public class JdbcProcessedEventStore implements ProcessedEventStore {

  private final ProcessedEvents processedEvents;

  @Override
  public boolean markProcessed(UUID eventId, String eventType) {
    return processedEvents.markProcessed(eventId, eventType);
  }
}
