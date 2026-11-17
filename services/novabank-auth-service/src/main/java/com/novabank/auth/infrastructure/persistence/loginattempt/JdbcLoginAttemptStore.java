package com.novabank.auth.infrastructure.persistence.loginattempt;

import com.novabank.auth.application.port.persistence.LoginAttemptStore;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * One atomic upsert per failure, so concurrent attempts can't lose updates. Writes use their own
 * transaction: the sign-in itself fails (its exception rolls back the caller), but the failure must
 * still be counted.
 */
@Component
public class JdbcLoginAttemptStore implements LoginAttemptStore {

  private final JdbcClient jdbcClient;

  public JdbcLoginAttemptStore(JdbcClient jdbcClient) {
    this.jdbcClient = jdbcClient;
  }

  @Override
  public Optional<LoginAttempts> find(UUID userId) {
    return jdbcClient
        .sql("SELECT failed_count, locked_until FROM login_attempts WHERE user_id = :userId")
        .param("userId", userId)
        .query(JdbcLoginAttemptStore::map)
        .optional();
  }

  @Override
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public LoginAttempts recordFailure(
      UUID userId, Instant now, Instant windowStart, int maxFailures, Instant lockedUntil) {
    // "count" = this failure plus the previous ones still inside the window.
    return jdbcClient
        .sql(
            """
            INSERT INTO login_attempts AS a (user_id, failed_count, last_failed_at)
            VALUES (:userId, 1, :now)
            ON CONFLICT (user_id) DO UPDATE SET
                failed_count = CASE
                    WHEN (CASE WHEN a.last_failed_at < :windowStart THEN 1
                               ELSE a.failed_count + 1 END) >= :max THEN 0
                    ELSE (CASE WHEN a.last_failed_at < :windowStart THEN 1
                               ELSE a.failed_count + 1 END)
                END,
                locked_until = CASE
                    WHEN (CASE WHEN a.last_failed_at < :windowStart THEN 1
                               ELSE a.failed_count + 1 END) >= :max THEN :lockedUntil
                    ELSE a.locked_until
                END,
                last_failed_at = :now
            RETURNING failed_count, locked_until
            """)
        .param("userId", userId)
        .param("now", Timestamp.from(now))
        .param("windowStart", Timestamp.from(windowStart))
        .param("max", maxFailures)
        .param("lockedUntil", Timestamp.from(lockedUntil))
        .query(JdbcLoginAttemptStore::map)
        .single();
  }

  @Override
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void reset(UUID userId) {
    jdbcClient
        .sql("DELETE FROM login_attempts WHERE user_id = :userId")
        .param("userId", userId)
        .update();
  }

  private static LoginAttempts map(ResultSet rs, int rowNum) throws SQLException {
    Timestamp lockedUntil = rs.getTimestamp("locked_until");
    return new LoginAttempts(
        rs.getInt("failed_count"), lockedUntil == null ? null : lockedUntil.toInstant());
  }
}
