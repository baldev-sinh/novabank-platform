package com.novabank.account.application.response;

import com.novabank.account.application.port.funds.AccountTransactionStore;
import java.time.Instant;
import java.util.UUID;

/**
 * One line of an account's history. Amounts are decimal strings with a currency (Decision L).
 *
 * @param direction {@code CREDIT} = money in, {@code DEBIT} = money out
 * @param counterpartyLast4 the other account of a transfer, last 4 digits only; null for deposits
 */
public record TransactionResponse(
    long sequence,
    UUID paymentId,
    String type,
    String direction,
    String amount,
    String currency,
    String balanceAfter,
    String counterpartyLast4,
    String reference,
    Instant postedAt) {

  public static TransactionResponse from(AccountTransactionStore.View view) {
    return new TransactionResponse(
        view.sequence(),
        view.paymentId(),
        view.type(),
        view.direction(),
        view.amount().amountString(),
        view.amount().currencyCode(),
        view.balanceAfter().amountString(),
        view.counterpartyLast4(),
        view.reference(),
        view.postedAt());
  }
}
