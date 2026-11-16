package com.novabank.ledger.application.response;

import com.novabank.ledger.domain.model.LedgerAccount;
import java.util.UUID;

public record LedgerAccountResponse(
    UUID accountId,
    String kind,
    String accountClass,
    String currency,
    String balance,
    long lastSequence) {

  public static LedgerAccountResponse from(LedgerAccount account) {
    return new LedgerAccountResponse(
        account.id(),
        account.kind().name(),
        account.accountClass().name(),
        account.currency().getCurrencyCode(),
        account.balance().amountString(),
        account.lastSequence());
  }
}
