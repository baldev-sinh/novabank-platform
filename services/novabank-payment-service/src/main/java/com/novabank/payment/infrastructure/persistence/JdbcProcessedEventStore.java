package com.novabank.payment.infrastructure.persistence;

import com.novabank.payment.application.port.ProcessedEventStore;
import com.novabank.platform.messaging.consumer.ProcessedEvents;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class JdbcProcessedEventStore implements ProcessedEventStore {

  private final ProcessedEvents processedEvents;

  @Override
  public boolean markProcessed(UUID eventId, String eventType) {
    return processedEvents.markProcessed(eventId, eventType);
  }
}
