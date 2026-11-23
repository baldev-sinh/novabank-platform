package com.novabank.account.application.event;

import com.novabank.account.domain.model.Account;
import java.time.Instant;
import java.util.UUID;

/**
 * The account is ready to use: the ledger confirmed it (PENDING → ACTIVE, 7.5). Published on {@code
 * account.events.v1}, keyed by the account, so it follows AccountOpened. The notification service
 * tells the owner.
 */
public record AccountActivatedEvent(
    UUID eventId,
    UUID accountId,
    String accountNumber,
    UUID ownerUserId,
    String currency,
    Instant occurredAt)
    implements AccountEvent {

  public static final String TYPE = "AccountActivated.v1";

  @Override
  public String eventType() {
    return TYPE;
  }

  @Override
  public UUID key() {
    return accountId;
  }

  @Override
  public String keyType() {
    return AccountOpenedEvent.AGGREGATE_TYPE;
  }

  public static AccountActivatedEvent of(Account account, Instant occurredAt) {
    return new AccountActivatedEvent(
        UUID.randomUUID(),
        account.id().value(),
        account.number().value(),
        account.ownerUserId(),
        account.currency().getCurrencyCode(),
        occurredAt);
  }
}
