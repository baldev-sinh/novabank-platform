package com.novabank.account.infrastructure.persistence.funds;

import com.novabank.account.application.port.funds.HoldRejectionStore;
import com.novabank.account.domain.model.HoldRejectionReason;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class JdbcHoldRejectionStore implements HoldRejectionStore {

  private final JdbcClient jdbcClient;

  @Override
  public void save(UUID paymentId, HoldRejectionReason reason, Instant rejectedAt) {
    jdbcClient
        .sql(
            "INSERT INTO hold_rejections (payment_id, reason, rejected_at)"
                + " VALUES (:paymentId, :reason, :rejectedAt)")
        .param("paymentId", paymentId)
        .param("reason", reason.name())
        .param("rejectedAt", Timestamp.from(rejectedAt))
        .update();
  }

  @Override
  public Optional<HoldRejectionReason> find(UUID paymentId) {
    return jdbcClient
        .sql("SELECT reason FROM hold_rejections WHERE payment_id = :paymentId")
        .param("paymentId", paymentId)
        .query(String.class)
        .optional()
        .map(HoldRejectionReason::valueOf);
  }
}
