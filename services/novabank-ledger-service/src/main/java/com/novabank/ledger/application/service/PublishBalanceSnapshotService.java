package com.novabank.ledger.application.service;

import com.novabank.ledger.application.event.LedgerBalanceSnapshotEvent;
import com.novabank.ledger.application.event.LedgerBalanceSnapshotEvent.AccountBalance;
import com.novabank.ledger.application.port.BalanceSnapshotQuery;
import com.novabank.ledger.application.port.LedgerEventOutbox;
import com.novabank.ledger.application.usecase.PublishBalanceSnapshotUseCase;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The balance snapshot for drift detection (6.2). Each account's balance and sequence come from one
 * row, so the account service can compare them exactly: a projection at the same sequence must show
 * the same balance. Accounts that change while the snapshot runs are simply compared later.
 */
@Service
public class PublishBalanceSnapshotService implements PublishBalanceSnapshotUseCase {

  private final BalanceSnapshotQuery balances;
  private final LedgerEventOutbox events;
  private final Clock clock;
  private final int batchSize;

  public PublishBalanceSnapshotService(
      BalanceSnapshotQuery balances,
      LedgerEventOutbox events,
      Clock clock,
      @Value("${ledger.snapshot.batch-size:500}") int batchSize) {
    this.balances = balances;
    this.events = events;
    this.clock = clock;
    this.batchSize = batchSize;
  }

  @Override
  @Transactional
  public int publish() {
    if (!balances.tryLockSnapshot()) {
      return -1;
    }
    UUID snapshotId = UUID.randomUUID();
    Instant takenAt = clock.instant();
    int published = 0;
    int part = 0;
    UUID after = null;
    List<AccountBalance> page;
    do {
      page = balances.customerBalancesAfter(after, batchSize);
      if (!page.isEmpty()) {
        part++;
        events.add(
            new LedgerBalanceSnapshotEvent(UUID.randomUUID(), snapshotId, takenAt, part, page));
        published += page.size();
        after = page.getLast().accountId();
      }
    } while (page.size() == batchSize);
    return published;
  }
}
