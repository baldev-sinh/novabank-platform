package com.novabank.account.infrastructure.persistence.funds;

import com.novabank.account.application.port.funds.BalanceProjectionQuery;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class JdbcBalanceProjectionQuery implements BalanceProjectionQuery {

  private final JdbcClient jdbcClient;

  @Override
  public Map<UUID, ProjectedBalance> find(Collection<UUID> accountIds) {
    Map<UUID, ProjectedBalance> found = new HashMap<>();
    if (accountIds.isEmpty()) {
      return found;
    }
    jdbcClient
        .sql(
            """
            SELECT a.id,
                   COALESCE(b.ledger_balance, 0) AS ledger_balance,
                   COALESCE(b.last_ledger_sequence, 0) AS last_ledger_sequence
            FROM accounts a LEFT JOIN account_balances b ON b.account_id = a.id
            WHERE a.id IN (:ids)
            """)
        .param("ids", accountIds)
        .query((rs, rowNum) ->
            found.put(
                rs.getObject("id", UUID.class),
                new ProjectedBalance(
                    rs.getBigDecimal("ledger_balance"), rs.getLong("last_ledger_sequence"))))
        .list();
    return found;
  }
}
