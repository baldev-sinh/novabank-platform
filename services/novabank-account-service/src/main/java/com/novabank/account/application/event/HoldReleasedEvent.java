package com.novabank.account.application.event;

import java.time.Instant;
import java.util.UUID;

/**
 * Answer to ReleaseHold. {@code outcome}: RELEASED, NO_HOLD (nothing was held) or ALREADY_CAPTURED.
 * Key = paymentId, so the saga sees one payment's replies in order.
 */
public record HoldReleasedEvent(UUID eventId, UUID paymentId, String outcome, Instant occurredAt)
    implements AccountEvent {

  public static final String TYPE = "HoldReleased.v1";

  public static HoldReleasedEvent of(UUID paymentId, String outcome, Instant occurredAt) {
    return new HoldReleasedEvent(UUID.randomUUID(), paymentId, outcome, occurredAt);
  }

  @Override
  public String eventType() {
    return TYPE;
  }

  @Override
  public UUID key() {
    return paymentId;
  }

  @Override
  public String keyType() {
    return "Payment";
  }
}
