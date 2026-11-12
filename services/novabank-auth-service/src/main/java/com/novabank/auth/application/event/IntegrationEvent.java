package com.novabank.auth.application.event;

import java.time.Instant;
import java.util.UUID;

/** A fact this service publishes for other services. Published at least once via the outbox. */
public sealed interface IntegrationEvent permits UserRegisteredEvent {

  /** Unique per event; consumers use it to ignore duplicates. */
  UUID eventId();

  /** Versioned type name, e.g. {@code UserRegistered.v1}. */
  String eventType();

  String aggregateType();

  /** Events for the same aggregate are delivered in order. */
  UUID aggregateId();

  Instant occurredAt();
}
