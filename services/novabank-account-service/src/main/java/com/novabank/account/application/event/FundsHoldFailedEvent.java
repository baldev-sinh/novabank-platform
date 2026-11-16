package com.novabank.account.application.event;

import java.time.Instant;
import java.util.UUID;

/**
 * Nothing was reserved, and nothing ever will be for this payment. Key = paymentId, so the saga
 * sees one payment's replies in order.
 */
public record FundsHoldFailedEvent(UUID eventId, UUID paymentId, String reason, Instant occurredAt)
    implements AccountEvent {

  public static final String TYPE = "FundsHoldFailed.v1";

  public static FundsHoldFailedEvent of(UUID paymentId, String reason, Instant occurredAt) {
    return new FundsHoldFailedEvent(UUID.randomUUID(), paymentId, reason, occurredAt);
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
