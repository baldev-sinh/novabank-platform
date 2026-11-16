package com.novabank.account.application.response;

import com.novabank.account.domain.model.AccountFunds;
import java.util.UUID;

/**
 * @param ledgerBalance what the ledger says the account holds (eventually consistent)
 * @param held reserved by payments in progress
 * @param available what can be spent now: ledger balance minus held
 * @param ledgerSequence how far the projection is: the last ledger entry applied
 */
public record BalanceResponse(
    UUID accountId,
    String currency,
    String ledgerBalance,
    String held,
    String available,
    long ledgerSequence) {

  public static BalanceResponse from(AccountFunds funds) {
    return new BalanceResponse(
        funds.accountId(),
        funds.currency().getCurrencyCode(),
        funds.ledgerBalance().amountString(),
        funds.held().amountString(),
        funds.available().amountString(),
        funds.lastLedgerSequence());
  }
}
