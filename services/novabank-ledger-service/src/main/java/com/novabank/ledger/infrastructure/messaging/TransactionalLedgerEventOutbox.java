package com.novabank.ledger.infrastructure.messaging;

import com.novabank.ledger.application.event.LedgerAccountOpenedEvent;
import com.novabank.ledger.application.event.LedgerBalanceSnapshotEvent;
import com.novabank.ledger.application.event.LedgerPostingFailedEvent;
import com.novabank.ledger.application.event.LedgerTransactionPostedEvent;
import com.novabank.ledger.application.port.LedgerEventOutbox;
import com.novabank.platform.messaging.outbox.OutboxRecord;
import com.novabank.platform.messaging.outbox.OutboxWriter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Payment events are keyed by payment id: the saga sees one payment's events in order. Account
 * lifecycle events are keyed by account id.
 */
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

  /** Keyed by account id: one account's lifecycle events stay in order. */
  @Override
  public void add(LedgerAccountOpenedEvent event) {
    outboxWriter.append(
        new OutboxRecord(
            event.eventId(),
            events.ledgerEvents(),
            LedgerAccountOpenedEvent.TYPE,
            "LedgerAccount",
            event.accountId(),
            event,
            event.openedAt()));
  }

  /** Keyed by snapshot id: the parts of one snapshot stay together. */
  @Override
  public void add(LedgerBalanceSnapshotEvent event) {
    outboxWriter.append(
        new OutboxRecord(
            event.eventId(),
            events.ledgerEvents(),
            LedgerBalanceSnapshotEvent.TYPE,
            "LedgerSnapshot",
            event.snapshotId(),
            event,
            event.takenAt()));
  }
}
