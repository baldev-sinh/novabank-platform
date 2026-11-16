package com.novabank.account.application.event;

import com.novabank.account.domain.model.Account;
import java.time.Instant;
import java.util.UUID;

/**
 * A new account exists. Published on {@code account.events.v1}; the ledger creates the matching
 * ledger account from it.
 */
public record AccountOpenedEvent(
    UUID eventId,
    UUID accountId,
    String accountNumber,
    UUID customerId,
    UUID ownerUserId,
    String type,
    String currency,
    Instant occurredAt) {

  public static final String TYPE = "AccountOpened.v1";
  public static final String AGGREGATE_TYPE = "Account";

  public static AccountOpenedEvent of(Account account) {
    return new AccountOpenedEvent(
        UUID.randomUUID(),
        account.id().value(),
        account.number().value(),
        account.customerId(),
        account.ownerUserId(),
        account.type().name(),
        account.currency().getCurrencyCode(),
        account.openedAt());
  }
}
