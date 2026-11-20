package com.novabank.ledger.application.port;

import com.novabank.ledger.application.event.LedgerBalanceSnapshotEvent.AccountBalance;
import java.util.List;
import java.util.UUID;

/** Reads customer balances for the snapshot. Called inside one transaction. */
public interface BalanceSnapshotQuery {

  /**
   * Takes the snapshot lock for this transaction, so only one ledger instance snapshots at a time.
   *
   * @return {@code false} if another instance holds it
   */
  boolean tryLockSnapshot();

  /** Customer accounts ordered by id, starting after {@code afterId} (null for the first page). */
  List<AccountBalance> customerBalancesAfter(UUID afterId, int limit);
}
