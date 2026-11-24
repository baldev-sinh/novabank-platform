package com.novabank.account.infrastructure.persistence.funds;

import com.novabank.account.application.port.funds.AccountTransactionStore;
import com.novabank.platform.money.Money;
import java.sql.Timestamp;
import java.util.Currency;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class JdbcAccountTransactionStore implements AccountTransactionStore {

  private final JdbcClient jdbcClient;

  @Override
  public void record(Line line) {
    jdbcClient
        .sql(
            """
            INSERT INTO account_transactions
                (account_id, sequence, transaction_id, payment_id, type, direction, amount,
                 currency, balance_after, counterparty_account_id, reference, posted_at)
            VALUES (:accountId, :sequence, :transactionId, :paymentId, :type, :direction, :amount,
                    :currency, :balanceAfter, :counterparty, :reference, :postedAt)
            ON CONFLICT (account_id, sequence) DO NOTHING
            """)
        .param("accountId", line.accountId())
        .param("sequence", line.sequence())
        .param("transactionId", line.transactionId())
        .param("paymentId", line.paymentId())
        .param("type", line.type())
        .param("direction", line.direction())
        .param("amount", line.amount().amount())
        .param("currency", line.amount().currencyCode())
        .param("balanceAfter", line.balanceAfter().amount())
        .param("counterparty", line.counterpartyAccountId())
        .param("reference", line.reference())
        .param("postedAt", Timestamp.from(line.postedAt()))
        .update();
  }

  @Override
  public List<View> page(UUID accountId, Long beforeSequence, int limit) {
    // Keyset paging on the primary key: each page is one index range scan however deep it is.
    return jdbcClient
        .sql(
            """
            SELECT t.sequence, t.payment_id, t.type, t.direction, t.amount, t.currency,
                   t.balance_after, t.reference, t.posted_at,
                   right(c.account_number, 4) AS counterparty_last4
            FROM account_transactions t
            LEFT JOIN accounts c ON c.id = t.counterparty_account_id
            WHERE t.account_id = :accountId
              AND (CAST(:before AS BIGINT) IS NULL OR t.sequence < :before)
            ORDER BY t.sequence DESC
            LIMIT :limit
            """)
        .param("accountId", accountId)
        .param("before", beforeSequence)
        .param("limit", limit)
        .query((rs, rowNum) -> {
          Currency currency = Currency.getInstance(rs.getString("currency"));
          return new View(
              rs.getLong("sequence"),
              rs.getObject("payment_id", UUID.class),
              rs.getString("type"),
              rs.getString("direction"),
              new Money(rs.getBigDecimal("amount"), currency),
              new Money(rs.getBigDecimal("balance_after"), currency),
              rs.getString("counterparty_last4"),
              rs.getString("reference"),
              rs.getTimestamp("posted_at").toInstant());
        })
        .list();
  }
}
