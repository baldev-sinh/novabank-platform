package com.novabank.account.application.command;

import com.novabank.account.domain.model.AccountType;
import java.util.Objects;
import java.util.UUID;

/**
 * @param ownerUserId the caller (JWT subject), never taken from the request body
 * @param idempotencyKey the client's Idempotency-Key header
 * @param currency ISO 4217 code
 */
public record OpenAccountCommand(
    UUID ownerUserId, String idempotencyKey, AccountType type, String currency) {

  public OpenAccountCommand {
    Objects.requireNonNull(ownerUserId, "ownerUserId cannot be null");
    Objects.requireNonNull(idempotencyKey, "idempotencyKey cannot be null");
    Objects.requireNonNull(type, "type cannot be null");
    Objects.requireNonNull(currency, "currency cannot be null");
  }

  /** What makes two requests "the same" for the Idempotency-Key check. */
  public String fingerprint() {
    return type + "|" + currency;
  }
}
