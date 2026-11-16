package com.novabank.customer.infrastructure.persistence.event;

import com.novabank.customer.application.port.messaging.KnownUserStore;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class JdbcKnownUserStore implements KnownUserStore {

  private final JdbcTemplate jdbcTemplate;

  @Override
  public void save(UUID userId, String email, Instant registeredAt) {
    jdbcTemplate.update(
        "INSERT INTO known_users (user_id, email, registered_at) VALUES (?, ?, ?)"
            + " ON CONFLICT (user_id) DO UPDATE SET email = EXCLUDED.email",
        userId,
        email,
        Timestamp.from(registeredAt));
  }
}
