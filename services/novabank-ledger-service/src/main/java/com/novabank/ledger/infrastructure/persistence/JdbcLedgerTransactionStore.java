package com.novabank.ledger.infrastructure.persistence;

import com.novabank.ledger.application.port.LedgerTransactionStore;
import com.novabank.ledger.domain.model.Direction;
import com.novabank.ledger.domain.model.LedgerTransaction;
import com.novabank.ledger.domain.model.PostedEntry;
import com.novabank.platform.money.Money;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.Currency;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** Insert-only: the database rejects any UPDATE or DELETE of ledger rows (see V1). */
@Repository
@RequiredArgsConstructor
public class JdbcLedgerTransactionStore implements LedgerTransactionStore {

  private final JdbcClient jdbcClient;

  @Override
  public void save(LedgerTransaction transaction, List<PostedEntry> entries) {
    jdbcClient
        .sql(
            """
            INSERT INTO ledger_transactions (id, payment_id, type, posted_at)
            VALUES (:id, :paymentId, :type, :postedAt)
            """)
        .param("id", transaction.id())
        .param("paymentId", transaction.paymentId())
        .param("type", transaction.type().name())
        .param("postedAt", Timestamp.from(transaction.postedAt()))
        .update();
    for (PostedEntry entry : entries) {
      jdbcClient
          .sql(
              """
              INSERT INTO ledger_entries
                  (id, transaction_id, account_id, direction, amount, currency, balance_after,
                   account_sequence)
              VALUES (:id, :transactionId, :accountId, :direction, :amount, :currency,
                      :balanceAfter, :sequence)
              """)
          .param("id", UUID.randomUUID())
          .param("transactionId", transaction.id())
          .param("accountId", entry.accountId())
          .param("direction", entry.direction().name())
          .param("amount", entry.amount().amount())
          .param("currency", entry.amount().currencyCode())
          .param("balanceAfter", entry.balanceAfter().amount())
          .param("sequence", entry.sequence())
          .update();
    }
  }

  @Override
  public Optional<Stored> findByPaymentId(UUID paymentId) {
    return jdbcClient
        .sql("SELECT id, type, posted_at FROM ledger_transactions WHERE payment_id = :paymentId")
        .param("paymentId", paymentId)
        .query((rs, rowNum) ->
            new Stored(
                rs.getObject("id", UUID.class),
                rs.getString("type"),
                rs.getTimestamp("posted_at").toInstant(),
                List.of()))
        .optional()
        .map(stored ->
            new Stored(
                stored.transactionId(),
                stored.type(),
                stored.postedAt(),
                entriesOfTransaction(stored.transactionId())));
  }

  @Override
  public List<StoredEntry> entriesOf(UUID accountId, int limit) {
    return jdbcClient
        .sql(
            """
            SELECT e.*, t.payment_id, t.posted_at
            FROM ledger_entries e JOIN ledger_transactions t ON t.id = e.transaction_id
            WHERE e.account_id = :accountId
            ORDER BY e.account_sequence DESC
            LIMIT :limit
            """)
        .param("accountId", accountId)
        .param("limit", limit)
        .query((rs, rowNum) ->
            new StoredEntry(
                rs.getObject("transaction_id", UUID.class),
                rs.getObject("payment_id", UUID.class),
                rs.getTimestamp("posted_at").toInstant(),
                mapEntry(rs, rowNum)))
        .list();
  }

  private List<PostedEntry> entriesOfTransaction(UUID transactionId) {
    return jdbcClient
        .sql(
            "SELECT * FROM ledger_entries WHERE transaction_id = :id ORDER BY direction DESC, account_id")
        .param("id", transactionId)
        .query(JdbcLedgerTransactionStore::mapEntry)
        .list();
  }

  private static PostedEntry mapEntry(ResultSet rs, int rowNum) throws SQLException {
    Currency currency = Currency.getInstance(rs.getString("currency"));
    return new PostedEntry(
        rs.getObject("account_id", UUID.class),
        Direction.valueOf(rs.getString("direction")),
        new Money(rs.getBigDecimal("amount"), currency),
        new Money(rs.getBigDecimal("balance_after"), currency),
        rs.getLong("account_sequence"));
  }
}
