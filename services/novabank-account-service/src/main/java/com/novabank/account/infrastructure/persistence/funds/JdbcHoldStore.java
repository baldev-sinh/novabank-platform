package com.novabank.account.infrastructure.persistence.funds;

import com.novabank.account.application.port.funds.HoldStore;
import com.novabank.account.domain.model.Hold;
import com.novabank.account.domain.model.HoldStatus;
import com.novabank.platform.money.Money;
import java.sql.Timestamp;
import java.util.Currency;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class JdbcHoldStore implements HoldStore {

  private final JdbcClient jdbcClient;

  @Override
  public void save(Hold hold) {
    jdbcClient
        .sql(
            """
            INSERT INTO account_holds
                (id, payment_id, account_id, beneficiary_account_id, amount, currency, status,
                 created_at, updated_at)
            VALUES (:id, :paymentId, :accountId, :beneficiary, :amount, :currency, :status,
                    :createdAt, :createdAt)
            """)
        .param("id", hold.id())
        .param("paymentId", hold.paymentId())
        .param("accountId", hold.accountId())
        .param("beneficiary", hold.beneficiaryAccountId())
        .param("amount", hold.amount().amount())
        .param("currency", hold.amount().currencyCode())
        .param("status", hold.status().name())
        .param("createdAt", Timestamp.from(hold.createdAt()))
        .update();
  }

  @Override
  public Optional<Hold> findByPaymentId(UUID paymentId) {
    return jdbcClient
        .sql("SELECT * FROM account_holds WHERE payment_id = :paymentId")
        .param("paymentId", paymentId)
        .query((rs, rowNum) -> {
          Currency currency = Currency.getInstance(rs.getString("currency"));
          return new Hold(
              rs.getObject("id", UUID.class),
              rs.getObject("payment_id", UUID.class),
              rs.getObject("account_id", UUID.class),
              rs.getObject("beneficiary_account_id", UUID.class),
              new Money(rs.getBigDecimal("amount"), currency),
              HoldStatus.valueOf(rs.getString("status")),
              rs.getTimestamp("created_at").toInstant());
        })
        .optional();
  }

  @Override
  public void updateStatus(Hold hold) {
    jdbcClient
        .sql("UPDATE account_holds SET status = :status, updated_at = now() WHERE id = :id")
        .param("status", hold.status().name())
        .param("id", hold.id())
        .update();
  }
}
