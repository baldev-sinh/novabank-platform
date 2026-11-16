package com.novabank.payment.application.command;

import com.novabank.platform.money.Money;
import java.util.UUID;

/**
 * @param requestedBy the operations staff member (JWT subject)
 */
public record DepositCommand(
    UUID requestedBy, String idempotencyKey, UUID toAccountId, Money amount, String reference) {

  public String fingerprint() {
    return "DEPOSIT|" + toAccountId + "|" + amount + "|" + reference;
  }
}
