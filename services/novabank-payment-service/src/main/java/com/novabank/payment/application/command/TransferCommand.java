package com.novabank.payment.application.command;

import com.novabank.platform.money.Money;
import java.util.UUID;

/**
 * @param requestedBy the caller (JWT subject); the account service checks they own the account
 */
public record TransferCommand(
    UUID requestedBy,
    String idempotencyKey,
    UUID fromAccountId,
    String toAccountNumber,
    Money amount,
    String reference) {

  public String fingerprint() {
    return "TRANSFER|" + fromAccountId + "|" + toAccountNumber + "|" + amount + "|" + reference;
  }
}
