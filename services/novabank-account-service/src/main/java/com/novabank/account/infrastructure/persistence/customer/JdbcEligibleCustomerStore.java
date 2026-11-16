package com.novabank.account.infrastructure.persistence.customer;

import com.novabank.account.application.port.customer.EligibleCustomerStore;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class JdbcEligibleCustomerStore implements EligibleCustomerStore {

  private final JdbcClient jdbcClient;

  @Override
  public void save(UUID customerId, UUID userId, Instant verifiedAt) {
    jdbcClient
        .sql(
            """
            INSERT INTO eligible_customers (customer_id, user_id, verified_at)
            VALUES (:customerId, :userId, :verifiedAt)
            ON CONFLICT (customer_id) DO NOTHING
            """)
        .param("customerId", customerId)
        .param("userId", userId)
        .param("verifiedAt", Timestamp.from(verifiedAt))
        .update();
  }

  @Override
  public Optional<UUID> findCustomerIdByUserId(UUID userId) {
    return jdbcClient
        .sql("SELECT customer_id FROM eligible_customers WHERE user_id = :userId")
        .param("userId", userId)
        .query(UUID.class)
        .optional();
  }
}
