package com.novabank.auth.infrastructure.persistence.refreshtoken;

import com.novabank.auth.application.port.persistence.RefreshTokenStore;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class JdbcRefreshTokenStore implements RefreshTokenStore {

  private final JdbcClient jdbcClient;

  @Override
  public void save(NewRefreshToken token) {
    jdbcClient
        .sql(
            """
            INSERT INTO refresh_tokens
                (id, family_id, user_id, token_hash, created_at, expires_at, family_expires_at)
            VALUES (:id, :familyId, :userId, :hash, :createdAt, :expiresAt, :familyExpiresAt)
            """)
        .param("id", token.id())
        .param("familyId", token.familyId())
        .param("userId", token.userId())
        .param("hash", token.tokenHash())
        .param("createdAt", Timestamp.from(token.createdAt()))
        .param("expiresAt", Timestamp.from(token.expiresAt()))
        .param("familyExpiresAt", Timestamp.from(token.familyExpiresAt()))
        .update();
  }

  /** One conditional UPDATE: the row lock makes it atomic, so a token is consumed at most once. */
  @Override
  public Optional<Consumed> consume(String tokenHash, Instant now) {
    return jdbcClient
        .sql(
            """
            UPDATE refresh_tokens SET used_at = :now
            WHERE token_hash = :hash AND used_at IS NULL AND revoked_at IS NULL
              AND expires_at > :now AND family_expires_at > :now
            RETURNING user_id, family_id, family_expires_at
            """)
        .param("now", Timestamp.from(now))
        .param("hash", tokenHash)
        .query((rs, rowNum) ->
            new Consumed(
                rs.getObject("user_id", UUID.class),
                rs.getObject("family_id", UUID.class),
                rs.getTimestamp("family_expires_at").toInstant()))
        .optional();
  }

  @Override
  public Optional<Stored> find(String tokenHash) {
    return jdbcClient
        .sql(
            """
            SELECT user_id, family_id, used_at, revoked_at
            FROM refresh_tokens WHERE token_hash = :hash
            """)
        .param("hash", tokenHash)
        .query((rs, rowNum) ->
            new Stored(
                rs.getObject("user_id", UUID.class),
                rs.getObject("family_id", UUID.class),
                toInstant(rs.getTimestamp("used_at")),
                toInstant(rs.getTimestamp("revoked_at"))))
        .optional();
  }

  @Override
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void revokeFamily(UUID familyId, Instant now) {
    jdbcClient
        .sql(
            """
            UPDATE refresh_tokens SET revoked_at = :now
            WHERE family_id = :familyId AND revoked_at IS NULL
            """)
        .param("now", Timestamp.from(now))
        .param("familyId", familyId)
        .update();
  }

  @Override
  @Transactional
  public int deleteExpiredBefore(Instant cutoff, int limit) {
    return jdbcClient
        .sql(
            """
            DELETE FROM refresh_tokens WHERE id IN (
              SELECT id FROM refresh_tokens WHERE family_expires_at < :cutoff LIMIT :limit)
            """)
        .param("cutoff", Timestamp.from(cutoff))
        .param("limit", limit)
        .update();
  }

  private static Instant toInstant(Timestamp timestamp) {
    return timestamp == null ? null : timestamp.toInstant();
  }
}
