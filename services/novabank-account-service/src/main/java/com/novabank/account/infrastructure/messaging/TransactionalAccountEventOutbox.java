package com.novabank.account.infrastructure.messaging;

import com.novabank.account.application.event.AccountEvent;
import com.novabank.account.application.port.messaging.AccountEventOutbox;
import com.novabank.platform.messaging.outbox.OutboxRecord;
import com.novabank.platform.messaging.outbox.OutboxWriter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Adapter from the application's {@link AccountEventOutbox} port to the platform outbox. */
@Component
@RequiredArgsConstructor
public class TransactionalAccountEventOutbox implements AccountEventOutbox {

  private final OutboxWriter outboxWriter;
  private final EventsProperties events;

  @Override
  public void add(AccountEvent event) {
    outboxWriter.append(
        new OutboxRecord(
            event.eventId(),
            events.accountEvents().topic(),
            event.eventType(),
            event.keyType(),
            event.key(),
            event,
            event.occurredAt()));
  }
}
