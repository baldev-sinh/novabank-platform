package com.novabank.ledger.infrastructure.messaging;

import com.novabank.ledger.application.event.LedgerPostingFailedEvent;
import com.novabank.ledger.application.event.LedgerTransactionPostedEvent;
import com.novabank.ledger.application.port.LedgerEventOutbox;
import com.novabank.platform.messaging.outbox.OutboxRecord;
import com.novabank.platform.messaging.outbox.OutboxWriter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Ledger events are keyed by payment id: the saga sees one payment's events in order. */
@Component
@RequiredArgsConstructor
public class TransactionalLedgerEventOutbox implements LedgerEventOutbox {

  static final String AGGREGATE_TYPE = "Payment";

  private final OutboxWriter outboxWriter;
  private final EventsProperties events;

  @Override
  public void add(LedgerTransactionPostedEvent event) {
    outboxWriter.append(
        new OutboxRecord(
            event.eventId(),
            events.ledgerEvents(),
            LedgerTransactionPostedEvent.TYPE,
            AGGREGATE_TYPE,
            event.paymentId(),
            event,
            event.postedAt()));
  }

  @Override
  public void add(LedgerPostingFailedEvent event) {
    outboxWriter.append(
        new OutboxRecord(
            event.eventId(),
            events.ledgerEvents(),
            LedgerPostingFailedEvent.TYPE,
            AGGREGATE_TYPE,
            event.paymentId(),
            event,
            event.occurredAt()));
  }
}
