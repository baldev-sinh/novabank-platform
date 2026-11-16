package com.novabank.account.application.event;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * The projection has applied a ledger transaction (and captured its hold). The payment saga
 * completes on it. Key = paymentId.
 */
public record BalancesUpdatedEvent(
    UUID eventId, UUID paymentId, UUID transactionId, List<UUID> accountIds, Instant occurredAt)
    implements AccountEvent {

  public static final String TYPE = "BalancesUpdated.v1";

  public static BalancesUpdatedEvent of(
      UUID paymentId, UUID transactionId, List<UUID> accountIds, Instant occurredAt) {
    return new BalancesUpdatedEvent(
        UUID.randomUUID(), paymentId, transactionId, List.copyOf(accountIds), occurredAt);
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
