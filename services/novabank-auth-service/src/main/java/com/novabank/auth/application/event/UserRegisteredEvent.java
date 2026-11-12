package com.novabank.auth.application.event;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** A new user account was created. No password or hash: events are not for secrets. */
public record UserRegisteredEvent(UUID eventId, UUID userId, String email, Instant occurredAt)
    implements IntegrationEvent {

  public static final String TYPE = "UserRegistered.v1";

  public UserRegisteredEvent {
    Objects.requireNonNull(eventId, "eventId cannot be null");
    Objects.requireNonNull(userId, "userId cannot be null");
    Objects.requireNonNull(email, "email cannot be null");
    Objects.requireNonNull(occurredAt, "occurredAt cannot be null");
  }

  public static UserRegisteredEvent of(UUID userId, String email, Instant occurredAt) {
    return new UserRegisteredEvent(UUID.randomUUID(), userId, email, occurredAt);
  }

  @Override
  public String eventType() {
    return TYPE;
  }

  @Override
  public String aggregateType() {
    return "User";
  }

  @Override
  public UUID aggregateId() {
    return userId;
  }
}
