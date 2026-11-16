package com.novabank.customer.application.event;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * The customer passed KYC and may now open accounts. Published on {@code customer.events.v1}. Ids
 * only: no name, date of birth or phone number in events.
 *
 * @param verifiedBy the compliance officer's user id
 */
public record CustomerVerifiedEvent(
    UUID eventId, UUID customerId, UUID userId, UUID verifiedBy, Instant occurredAt) {

  public static final String TYPE = "CustomerVerified.v1";
  public static final String AGGREGATE_TYPE = "Customer";

  public CustomerVerifiedEvent {
    Objects.requireNonNull(eventId, "eventId cannot be null");
    Objects.requireNonNull(customerId, "customerId cannot be null");
    Objects.requireNonNull(userId, "userId cannot be null");
    Objects.requireNonNull(verifiedBy, "verifiedBy cannot be null");
    Objects.requireNonNull(occurredAt, "occurredAt cannot be null");
  }

  public static CustomerVerifiedEvent of(
      UUID customerId, UUID userId, UUID verifiedBy, Instant occurredAt) {
    return new CustomerVerifiedEvent(UUID.randomUUID(), customerId, userId, verifiedBy, occurredAt);
  }
}
