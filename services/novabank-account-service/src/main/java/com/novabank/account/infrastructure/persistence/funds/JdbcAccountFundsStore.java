package com.novabank.account.infrastructure.persistence.funds;

import com.novabank.account.application.port.funds.AccountFundsStore;
import com.novabank.account.domain.model.AccountFunds;
import com.novabank.account.domain.model.AccountFunds.Beneficiary;
import com.novabank.account.domain.model.AccountStatus;
import com.novabank.account.domain.valueobject.AccountNumber;
import com.novabank.platform.money.Money;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Currency;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class JdbcAccountFundsStore implements AccountFundsStore {

  private static final String SELECT =
      """
      SELECT a.id, a.owner_user_id, a.status, a.currency,
             COALESCE(b.ledger_balance, 0) AS ledger_balance,
             COALESCE(b.last_ledger_sequence, 0) AS last_ledger_sequence,
             (SELECT COALESCE(sum(h.amount), 0) FROM account_holds h
              WHERE h.account_id = a.id AND h.status = 'ACTIVE') AS held
      FROM accounts a LEFT JOIN account_balances b ON b.account_id = a.id
      WHERE a.id = :id
      """;

  private final JdbcClient jdbcClient;

  /**
   * The balance row is created on first use, then locked. Holds are summed under that lock, so no
   * concurrent hold or capture can change them until this transaction ends.
   */
  @Override
  public Optional<AccountFunds> lock(UUID accountId) {
    jdbcClient
        .sql(
            """
            INSERT INTO account_balances (account_id, ledger_balance, last_ledger_sequence)
            SELECT id, 0, 0 FROM accounts WHERE id = :id
            ON CONFLICT (account_id) DO NOTHING
            """)
        .param("id", accountId)
        .update();
    boolean exists =
        jdbcClient
            .sql("SELECT account_id FROM account_balances WHERE account_id = :id FOR UPDATE")
            .param("id", accountId)
            .query(UUID.class)
            .optional()
            .isPresent();
    return exists ? find(accountId) : Optional.empty();
  }

  @Override
  public Optional<AccountFunds> find(UUID accountId) {
    return jdbcClient
        .sql(SELECT)
        .param("id", accountId)
        .query(JdbcAccountFundsStore::map)
        .optional();
  }

  @Override
  public Optional<Beneficiary> findBeneficiary(AccountNumber number) {
    return jdbcClient
        .sql("SELECT id, status, currency FROM accounts WHERE account_number = :number")
        .param("number", number.value())
        .query((rs, rowNum) ->
            new Beneficiary(
                rs.getObject("id", UUID.class),
                AccountStatus.valueOf(rs.getString("status")),
                Currency.getInstance(rs.getString("currency"))))
        .optional();
  }

  @Override
  public void saveProjection(AccountFunds funds) {
    jdbcClient
        .sql(
            """
            UPDATE account_balances
            SET ledger_balance = :balance, last_ledger_sequence = :sequence, updated_at = now()
            WHERE account_id = :id
            """)
        .param("balance", funds.ledgerBalance().amount())
        .param("sequence", funds.lastLedgerSequence())
        .param("id", funds.accountId())
        .update();
  }

  private static AccountFunds map(ResultSet rs, int rowNum) throws SQLException {
    Currency currency = Currency.getInstance(rs.getString("currency"));
    return new AccountFunds(
        rs.getObject("id", UUID.class),
        rs.getObject("owner_user_id", UUID.class),
        AccountStatus.valueOf(rs.getString("status")),
        currency,
        new Money(rs.getBigDecimal("ledger_balance"), currency),
        new Money(rs.getBigDecimal("held"), currency),
        rs.getLong("last_ledger_sequence"));
  }
}
