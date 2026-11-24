package com.novabank.ledger.application.command;

import com.novabank.ledger.domain.model.Direction;
import com.novabank.ledger.domain.model.TransactionType;
import com.novabank.platform.money.Money;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * @param commandId the message id (deduplicates redeliveries of this exact message)
 * @param paymentId the business key: one outcome per payment, however often it is sent
 * @param reference the payer's reference; optional
 */
public record PostLedgerTransactionCommand(
    UUID commandId,
    UUID paymentId,
    TransactionType type,
    List<Instruction> postings,
    String reference) {

  public PostLedgerTransactionCommand {
    Objects.requireNonNull(commandId, "commandId cannot be null");
    Objects.requireNonNull(paymentId, "paymentId cannot be null");
    Objects.requireNonNull(type, "type cannot be null");
    postings = List.copyOf(Objects.requireNonNull(postings, "postings cannot be null"));
  }

  /**
   * One posting. Exactly one of {@code accountId} (a customer account) or {@code systemAccount} (a
   * bank account such as {@code SETTLEMENT}, resolved per currency) is set.
   */
  public record Instruction(
      UUID accountId, String systemAccount, Direction direction, Money amount) {}
}
