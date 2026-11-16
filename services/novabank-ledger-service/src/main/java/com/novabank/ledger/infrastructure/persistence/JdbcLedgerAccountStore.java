package com.novabank.ledger.infrastructure.persistence;

import com.novabank.ledger.application.port.LedgerAccountStore;
import com.novabank.ledger.domain.model.AccountClass;
import com.novabank.ledger.domain.model.AccountKind;
import com.novabank.ledger.domain.model.LedgerAccount;
import com.novabank.platform.money.Money;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Collection;
import java.util.Currency;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class JdbcLedgerAccountStore implements LedgerAccountStore {

  private static final String COLUMNS = "id, kind, account_class, currency, balance, last_sequence";

  private final JdbcClient jdbcClient;

  @Override
  public void createCustomerAccount(UUID accountId, Currency currency, Instant createdAt) {
    jdbcClient
        .sql(
            """
            INSERT INTO ledger_accounts
                (id, kind, account_class, currency, balance, last_sequence, created_at)
            VALUES (:id, 'CUSTOMER', 'LIABILITY', :currency, 0, 0, :createdAt)
            ON CONFLICT (id) DO NOTHING
            """)
        .param("id", accountId)
        .param("currency", currency.getCurrencyCode())
        .param("createdAt", Timestamp.from(createdAt))
        .update();
  }

  @Override
  public Optional<UUID> findSystemAccountId(String code, Currency currency) {
    return jdbcClient
        .sql(
            "SELECT id FROM ledger_accounts"
                + " WHERE kind = 'SYSTEM' AND code = :code AND currency = :currency")
        .param("code", code)
        .param("currency", currency.getCurrencyCode())
        .query(UUID.class)
        .optional();
  }

  @Override
  public List<LedgerAccount> lockForUpdate(Collection<UUID> ids) {
    return jdbcClient
        .sql(
            "SELECT "
                + COLUMNS
                + " FROM ledger_accounts WHERE id = ANY(:ids)"
                + " ORDER BY id FOR UPDATE")
        .param("ids", ids.toArray(UUID[]::new))
        .query(JdbcLedgerAccountStore::map)
        .list();
  }

  @Override
  public void updateBalances(Collection<LedgerAccount> accounts) {
    for (LedgerAccount account : accounts) {
      jdbcClient
          .sql(
              "UPDATE ledger_accounts SET balance = :balance, last_sequence = :sequence"
                  + " WHERE id = :id")
          .param("balance", account.balance().amount())
          .param("sequence", account.lastSequence())
          .param("id", account.id())
          .update();
    }
  }

  @Override
  public Optional<LedgerAccount> findById(UUID id) {
    return jdbcClient
        .sql("SELECT " + COLUMNS + " FROM ledger_accounts WHERE id = :id")
        .param("id", id)
        .query(JdbcLedgerAccountStore::map)
        .optional();
  }

  private static LedgerAccount map(ResultSet rs, int rowNum) throws SQLException {
    Currency currency = Currency.getInstance(rs.getString("currency"));
    return new LedgerAccount(
        rs.getObject("id", UUID.class),
        AccountKind.valueOf(rs.getString("kind")),
        AccountClass.valueOf(rs.getString("account_class")),
        currency,
        new Money(rs.getBigDecimal("balance"), currency),
        rs.getLong("last_sequence"));
  }
}
