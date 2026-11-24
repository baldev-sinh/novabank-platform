package com.novabank.account.application.port.funds;

import com.novabank.platform.money.Money;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** The transaction history read model (8.1): one line per ledger entry on one of our accounts. */
public interface AccountTransactionStore {

  /** Idempotent per (account, sequence): a re-sent ledger event adds nothing. */
  void record(Line line);

  /**
   * Newest first.
   *
   * @param beforeSequence only lines older than this; null for the newest
   */
  List<View> page(UUID accountId, Long beforeSequence, int limit);

  /**
   * @param counterpartyAccountId the other account of a transfer; null for a deposit
   */
  record Line(
      UUID accountId,
      long sequence,
      UUID transactionId,
      UUID paymentId,
      String type,
      String direction,
      Money amount,
      Money balanceAfter,
      UUID counterpartyAccountId,
      String reference,
      Instant postedAt) {}

  /** A line as shown to its owner: the counterparty only by the last 4 digits of its number. */
  record View(
      long sequence,
      UUID paymentId,
      String type,
      String direction,
      Money amount,
      Money balanceAfter,
      String counterpartyLast4,
      String reference,
      Instant postedAt) {}
}
