package com.novabank.account.application.event;

import java.time.Instant;
import java.util.UUID;

/**
 * Money is reserved on the sender's account; the beneficiary was checked too. Key = paymentId, so
 * the saga sees one payment's replies in order.
 */
public record FundsHeldEvent(
    UUID eventId,
    UUID paymentId,
    UUID holdId,
    UUID accountId,
    UUID beneficiaryAccountId,
    String amount,
    String currency,
    Instant occurredAt)
    implements AccountEvent {

  public static final String TYPE = "FundsHeld.v1";

  public static FundsHeldEvent of(
      UUID paymentId,
      UUID holdId,
      UUID accountId,
      UUID beneficiaryAccountId,
      String amount,
      String currency,
      Instant occurredAt) {
    return new FundsHeldEvent(
        UUID.randomUUID(),
        paymentId,
        holdId,
        accountId,
        beneficiaryAccountId,
        amount,
        currency,
        occurredAt);
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
