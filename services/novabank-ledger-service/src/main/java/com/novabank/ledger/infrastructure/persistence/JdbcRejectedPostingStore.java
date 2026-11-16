package com.novabank.ledger.infrastructure.persistence;

import com.novabank.ledger.application.port.RejectedPostingStore;
import com.novabank.ledger.domain.model.RejectionReason;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class JdbcRejectedPostingStore implements RejectedPostingStore {

  private final JdbcClient jdbcClient;

  @Override
  public void save(UUID paymentId, RejectionReason reason, Instant rejectedAt) {
    jdbcClient
        .sql(
            """
            INSERT INTO rejected_postings (payment_id, reason, rejected_at)
            VALUES (:paymentId, :reason, :rejectedAt)
            """)
        .param("paymentId", paymentId)
        .param("reason", reason.name())
        .param("rejectedAt", Timestamp.from(rejectedAt))
        .update();
  }

  @Override
  public Optional<RejectionReason> find(UUID paymentId) {
    return jdbcClient
        .sql("SELECT reason FROM rejected_postings WHERE payment_id = :paymentId")
        .param("paymentId", paymentId)
        .query(String.class)
        .optional()
        .map(RejectionReason::valueOf);
  }
}
