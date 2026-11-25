package com.novabank.payment.infrastructure.persistence;

import com.novabank.payment.application.port.PaymentStore;
import com.novabank.payment.application.query.PaymentCursor;
import com.novabank.payment.domain.model.Payment;
import com.novabank.payment.domain.model.PaymentStatus;
import com.novabank.payment.domain.model.PaymentType;
import com.novabank.platform.money.Money;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Currency;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class JdbcPaymentStore implements PaymentStore {

  private final JdbcClient jdbcClient;

  @Override
  public void insert(Payment p, Instant nextAttemptAt) {
    jdbcClient
        .sql(
            """
            INSERT INTO payments
                (id, type, status, requested_by, from_account_id, to_account_number, to_account_id,
                 amount, currency, reference, failure_reason, hold_id, ledger_transaction_id,
                 attempts, next_attempt_at, needs_attention, created_at, updated_at)
            VALUES
                (:id, :type, :status, :requestedBy, :from, :toNumber, :toId, :amount, :currency,
                 :reference, NULL, NULL, NULL, 0, :next, false, :createdAt, :updatedAt)
            """)
        .param("id", p.id())
        .param("type", p.type().name())
        .param("status", p.status().name())
        .param("requestedBy", p.requestedBy())
        .param("from", p.fromAccountId())
        .param("toNumber", p.toAccountNumber())
        .param("toId", p.toAccountId())
        .param("amount", p.amount().amount())
        .param("currency", p.amount().currencyCode())
        .param("reference", p.reference())
        .param("next", Timestamp.from(nextAttemptAt))
        .param("createdAt", Timestamp.from(p.createdAt()))
        .param("updatedAt", Timestamp.from(p.updatedAt()))
        .update();
  }

  @Override
  public void update(Payment p, Instant nextAttemptAt, boolean newStep) {
    jdbcClient
        .sql(
            """
            UPDATE payments SET status = :status, to_account_id = :toId,
                failure_reason = :failure, hold_id = :holdId,
                ledger_transaction_id = :transactionId, updated_at = :updatedAt,
                next_attempt_at = :next,
                attempts = CASE WHEN :newStep THEN 0 ELSE attempts END,
                needs_attention = CASE WHEN :newStep THEN false ELSE needs_attention END
            WHERE id = :id
            """)
        .param("status", p.status().name())
        .param("toId", p.toAccountId())
        .param("failure", p.failureReason())
        .param("holdId", p.holdId())
        .param("transactionId", p.ledgerTransactionId())
        .param("updatedAt", Timestamp.from(p.updatedAt()))
        .param("next", p.status().isTerminal() ? null : Timestamp.from(nextAttemptAt))
        .param("newStep", newStep)
        .param("id", p.id())
        .update();
  }

  @Override
  public Optional<Payment> lock(UUID paymentId) {
    return jdbcClient
        .sql("SELECT * FROM payments WHERE id = :id FOR UPDATE")
        .param("id", paymentId)
        .query(JdbcPaymentStore::map)
        .optional();
  }

  @Override
  public Optional<Payment> find(UUID paymentId) {
    return jdbcClient
        .sql("SELECT * FROM payments WHERE id = :id")
        .param("id", paymentId)
        .query(JdbcPaymentStore::map)
        .optional();
  }

  @Override
  public List<Payment> findByRequester(UUID userId, PaymentCursor before, int limit) {
    // Keyset paging on idx_payments_requested_by_page (V4): one index range scan per page.
    if (before == null) {
      return jdbcClient
          .sql(
              """
              SELECT * FROM payments WHERE requested_by = :userId
              ORDER BY created_at DESC, id DESC LIMIT :limit
              """)
          .param("userId", userId)
          .param("limit", limit)
          .query(JdbcPaymentStore::map)
          .list();
    }
    return jdbcClient
        .sql(
            """
            SELECT * FROM payments
            WHERE requested_by = :userId AND (created_at, id) < (:createdAt, :paymentId)
            ORDER BY created_at DESC, id DESC LIMIT :limit
            """)
        .param("userId", userId)
        .param("createdAt", Timestamp.from(before.createdAt()))
        .param("paymentId", before.paymentId())
        .param("limit", limit)
        .query(JdbcPaymentStore::map)
        .list();
  }

  @Override
  public List<Overdue> lockOverdue(Instant now, int limit) {
    return jdbcClient
        .sql(
            """
            SELECT * FROM payments
            WHERE next_attempt_at <= :now
            ORDER BY next_attempt_at
            LIMIT :limit
            FOR UPDATE SKIP LOCKED
            """)
        .param("now", Timestamp.from(now))
        .param("limit", limit)
        .query((rs, rowNum) -> new Overdue(map(rs, rowNum), rs.getInt("attempts")))
        .list();
  }

  @Override
  public void recordAttempt(
      UUID paymentId, int attempts, Instant nextAttemptAt, boolean needsAttention) {
    jdbcClient
        .sql(
            """
            UPDATE payments SET attempts = :attempts, next_attempt_at = :next,
                needs_attention = needs_attention OR :attention
            WHERE id = :id
            """)
        .param("attempts", attempts)
        .param("next", Timestamp.from(nextAttemptAt))
        .param("attention", needsAttention)
        .param("id", paymentId)
        .update();
  }

  private static Payment map(ResultSet rs, int rowNum) throws SQLException {
    Currency currency = Currency.getInstance(rs.getString("currency"));
    return new Payment(
        rs.getObject("id", UUID.class),
        PaymentType.valueOf(rs.getString("type")),
        rs.getObject("requested_by", UUID.class),
        rs.getObject("from_account_id", UUID.class),
        rs.getString("to_account_number"),
        rs.getObject("to_account_id", UUID.class),
        new Money(rs.getBigDecimal("amount"), currency),
        rs.getString("reference"),
        PaymentStatus.valueOf(rs.getString("status")),
        rs.getString("failure_reason"),
        rs.getObject("hold_id", UUID.class),
        rs.getObject("ledger_transaction_id", UUID.class),
        rs.getTimestamp("created_at").toInstant(),
        rs.getTimestamp("updated_at").toInstant());
  }
}
