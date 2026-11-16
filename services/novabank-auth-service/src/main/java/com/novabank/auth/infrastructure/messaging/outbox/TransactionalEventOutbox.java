package com.novabank.auth.infrastructure.messaging.outbox;

import com.novabank.auth.application.event.IntegrationEvent;
import com.novabank.auth.application.port.messaging.EventOutbox;
import com.novabank.platform.messaging.outbox.OutboxRecord;
import com.novabank.platform.messaging.outbox.OutboxWriter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Adapter from the application's {@link EventOutbox} port to the platform outbox. */
@Component
@RequiredArgsConstructor
public class TransactionalEventOutbox implements EventOutbox {

  private final OutboxWriter outboxWriter;
  private final UserEventsProperties userEvents;

  /** The writer requires an active transaction: the event commits with the business change. */
  @Override
  public void add(IntegrationEvent event) {
    outboxWriter.append(
        new OutboxRecord(
            event.eventId(),
            userEvents.topic(),
            event.eventType(),
            event.aggregateType(),
            event.aggregateId(),
            event,
            event.occurredAt()));
  }
}
