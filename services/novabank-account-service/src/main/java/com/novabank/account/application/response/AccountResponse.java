package com.novabank.account.application.response;

import com.novabank.account.domain.model.Account;
import com.novabank.account.domain.model.AccountStatus;
import com.novabank.account.domain.model.AccountType;
import java.time.Instant;
import java.util.UUID;

public record AccountResponse(
    UUID accountId,
    String accountNumber,
    AccountType type,
    String currency,
    AccountStatus status,
    Instant openedAt) {

  public static AccountResponse from(Account account) {
    return new AccountResponse(
        account.id().value(),
        account.number().value(),
        account.type(),
        account.currency().getCurrencyCode(),
        account.status(),
        account.openedAt());
  }
}
