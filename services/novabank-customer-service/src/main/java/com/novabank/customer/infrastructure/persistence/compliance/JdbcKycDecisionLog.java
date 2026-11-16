package com.novabank.customer.infrastructure.persistence.compliance;

import com.novabank.customer.application.port.compliance.KycDecisionLog;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** Insert-only: decisions are never updated or deleted. */
@Repository
@RequiredArgsConstructor
public class JdbcKycDecisionLog implements KycDecisionLog {

  private final JdbcClient jdbcClient;

  @Override
  public void recordApproval(UUID customerId, UUID decidedBy, Instant decidedAt) {
    jdbcClient
        .sql(
            """
            INSERT INTO kyc_decisions (id, customer_id, decision, decided_by, decided_at)
            VALUES (:id, :customerId, 'APPROVED', :decidedBy, :decidedAt)
            """)
        .param("id", UUID.randomUUID())
        .param("customerId", customerId)
        .param("decidedBy", decidedBy)
        .param("decidedAt", Timestamp.from(decidedAt))
        .update();
  }
}
