package com.novabank.ledger.infrastructure.persistence;

import com.novabank.ledger.application.port.ReconciliationQuery;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class JdbcReconciliationQuery implements ReconciliationQuery {

  private final JdbcClient jdbcClient;

  @Override
  public List<String> accountsWithBalanceMismatch() {
    return jdbcClient
        .sql(
            """
            SELECT a.id::text
            FROM ledger_accounts a
            LEFT JOIN ledger_entries e ON e.account_id = a.id
            GROUP BY a.id, a.balance, a.account_class
            HAVING a.balance <> COALESCE(sum(
                CASE WHEN (a.account_class = 'ASSET') = (e.direction = 'DEBIT')
                     THEN e.amount ELSE -e.amount END), 0)
            ORDER BY a.id
            """)
        .query(String.class)
        .list();
  }

  @Override
  public List<String> unbalancedCurrencies() {
    return jdbcClient
        .sql(
            """
            SELECT currency FROM ledger_entries
            GROUP BY currency
            HAVING sum(CASE WHEN direction = 'DEBIT' THEN amount ELSE -amount END) <> 0
            ORDER BY currency
            """)
        .query(String.class)
        .list();
  }
}
