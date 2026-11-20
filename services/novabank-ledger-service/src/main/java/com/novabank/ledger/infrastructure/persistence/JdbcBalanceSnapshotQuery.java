package com.novabank.ledger.infrastructure.persistence;

import com.novabank.ledger.application.event.LedgerBalanceSnapshotEvent.AccountBalance;
import com.novabank.ledger.application.port.BalanceSnapshotQuery;
import com.novabank.platform.money.Money;
import java.util.Currency;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class JdbcBalanceSnapshotQuery implements BalanceSnapshotQuery {

  /** Arbitrary but fixed key for pg_try_advisory_xact_lock. */
  private static final long SNAPSHOT_LOCK_KEY = 0x4C45444745524E42L;

  private final JdbcClient jdbcClient;

  /** Released automatically when the transaction ends. */
  @Override
  public boolean tryLockSnapshot() {
    return Boolean.TRUE.equals(
        jdbcClient
            .sql("SELECT pg_try_advisory_xact_lock(:key)")
            .param("key", SNAPSHOT_LOCK_KEY)
            .query(Boolean.class)
            .single());
  }

  /** Keyset paging on the primary key; balance and sequence are read from the same row. */
  @Override
  public List<AccountBalance> customerBalancesAfter(UUID afterId, int limit) {
    return jdbcClient
        .sql(
            """
            SELECT id, balance, currency, last_sequence
            FROM ledger_accounts
            WHERE kind = 'CUSTOMER' AND (CAST(:afterId AS uuid) IS NULL OR id > :afterId)
            ORDER BY id
            LIMIT :limit
            """)
        .param("afterId", afterId)
        .param("limit", limit)
        .query((rs, rowNum) -> {
          String currency = rs.getString("currency");
          return new AccountBalance(
              rs.getObject("id", UUID.class),
              new Money(rs.getBigDecimal("balance"), Currency.getInstance(currency)).amountString(),
              currency,
              rs.getLong("last_sequence"));
        })
        .list();
  }
}
