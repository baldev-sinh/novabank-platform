package com.novabank.account.application.event;

import java.time.Instant;
import java.util.UUID;

/** Everything this service publishes on {@code account.events.v1}. */
public sealed interface AccountEvent
    permits AccountOpenedEvent,
        AccountActivatedEvent,
        FundsHeldEvent,
        FundsHoldFailedEvent,
        HoldReleasedEvent,
        BalancesUpdatedEvent {

  UUID eventId();

  /** Versioned type name, sent as the {@code eventType} header. */
  String eventType();

  /** Kafka key: the account for lifecycle events, the payment for saga replies. */
  UUID key();

  String keyType();

  Instant occurredAt();
}
