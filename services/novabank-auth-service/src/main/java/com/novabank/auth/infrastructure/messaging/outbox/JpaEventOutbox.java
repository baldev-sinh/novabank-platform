package com.novabank.auth.infrastructure.messaging.outbox;

import com.novabank.auth.application.event.IntegrationEvent;
import com.novabank.auth.application.port.messaging.EventOutbox;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

@Component
@RequiredArgsConstructor
public class JpaEventOutbox implements EventOutbox {

  private final SpringDataOutboxRepository repository;
  private final JsonMapper jsonMapper;

  /** MANDATORY: fails fast if someone calls this outside the business transaction. */
  @Override
  @Transactional(propagation = Propagation.MANDATORY)
  public void add(IntegrationEvent event) {
    repository.save(
        OutboxEventEntity.of(
            event.eventId(),
            event.aggregateType(),
            event.aggregateId(),
            event.eventType(),
            jsonMapper.writeValueAsString(event),
            event.occurredAt()));
  }
}
