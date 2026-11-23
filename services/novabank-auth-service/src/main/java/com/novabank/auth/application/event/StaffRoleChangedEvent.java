package com.novabank.auth.application.event;

import com.novabank.auth.domain.model.RoleName;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * An administrator granted or revoked a staff role (7.4): the audit trail of who may do what. Ids
 * only, no emails. Types {@code StaffRoleGranted.v1} and {@code StaffRoleRevoked.v1}, keyed by the
 * user, so a user's role changes are delivered in order.
 *
 * @param changedBy the administrator who made the change
 */
public record StaffRoleChangedEvent(
    UUID eventId, UUID userId, RoleName role, UUID changedBy, boolean granted, Instant occurredAt)
    implements IntegrationEvent {

  public static final String GRANTED = "StaffRoleGranted.v1";
  public static final String REVOKED = "StaffRoleRevoked.v1";

  public StaffRoleChangedEvent {
    Objects.requireNonNull(eventId, "eventId cannot be null");
    Objects.requireNonNull(userId, "userId cannot be null");
    Objects.requireNonNull(role, "role cannot be null");
    Objects.requireNonNull(changedBy, "changedBy cannot be null");
    Objects.requireNonNull(occurredAt, "occurredAt cannot be null");
  }

  public static StaffRoleChangedEvent granted(
      UUID userId, RoleName role, UUID changedBy, Instant occurredAt) {
    return new StaffRoleChangedEvent(UUID.randomUUID(), userId, role, changedBy, true, occurredAt);
  }

  public static StaffRoleChangedEvent revoked(
      UUID userId, RoleName role, UUID changedBy, Instant occurredAt) {
    return new StaffRoleChangedEvent(UUID.randomUUID(), userId, role, changedBy, false, occurredAt);
  }

  @Override
  public String eventType() {
    return granted ? GRANTED : REVOKED;
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
