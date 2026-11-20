package com.novabank.account.application.usecase;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/** Compares the balance projection with a ledger balance snapshot (6.2). Read-only. */
public interface CheckProjectionUseCase {

  Result check(List<LedgerBalance> snapshot);

  /** One account in a LedgerBalanceSnapshot. */
  record LedgerBalance(UUID accountId, BigDecimal balance, long sequence) {}

  /**
   * @param matched same sequence, same balance
   * @param drifted same sequence, different balance: the projection is wrong
   * @param lagging the projection hasn't applied the ledger's latest entries yet (normal briefly)
   * @param ahead the projection is newer than the snapshot (the snapshot is stale)
   * @param unknown accounts the ledger has but this service doesn't
   */
  record Result(int matched, List<UUID> drifted, int lagging, int ahead, int unknown) {

    public Result {
      drifted = List.copyOf(drifted);
    }
  }
}
