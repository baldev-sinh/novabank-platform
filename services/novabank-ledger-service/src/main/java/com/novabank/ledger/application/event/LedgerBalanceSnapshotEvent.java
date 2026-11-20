package com.novabank.ledger.application.event;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Part of a periodic snapshot of customer balances (6.2), so the account service can check its
 * projection against the source of truth. Published on {@code ledger.events.v1}, keyed by
 * snapshotId; one snapshot is split into parts of at most {@code ledger.snapshot.batch-size}.
 *
 * @param part 1-based number of this part within the snapshot
 */
public record LedgerBalanceSnapshotEvent(
    UUID eventId, UUID snapshotId, Instant takenAt, int part, List<AccountBalance> accounts) {

  public static final String TYPE = "LedgerBalanceSnapshot.v1";

  public LedgerBalanceSnapshotEvent {
    accounts = List.copyOf(accounts);
  }

  /**
   * @param balance decimal string in the account's currency (Decision L)
   * @param sequence the number of the account's last entry: the balance is the one right after it
   */
  public record AccountBalance(UUID accountId, String balance, String currency, long sequence) {}
}
