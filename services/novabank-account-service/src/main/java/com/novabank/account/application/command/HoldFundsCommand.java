package com.novabank.account.application.command;

import com.novabank.platform.money.Money;
import java.util.Objects;
import java.util.UUID;

/**
 * @param commandId the message id (deduplicates redeliveries of this exact message)
 * @param requestedBy the user who asked for the payment; must own {@code accountId}
 * @param beneficiaryAccountNumber where the money will go, checked now so a bad target fails before
 *     anything is reserved
 */
public record HoldFundsCommand(
    UUID commandId,
    UUID paymentId,
    UUID accountId,
    UUID requestedBy,
    String beneficiaryAccountNumber,
    Money amount) {

  public HoldFundsCommand {
    Objects.requireNonNull(commandId, "commandId cannot be null");
    Objects.requireNonNull(paymentId, "paymentId cannot be null");
    Objects.requireNonNull(accountId, "accountId cannot be null");
    Objects.requireNonNull(requestedBy, "requestedBy cannot be null");
    Objects.requireNonNull(beneficiaryAccountNumber, "beneficiaryAccountNumber cannot be null");
    Objects.requireNonNull(amount, "amount cannot be null");
  }
}
