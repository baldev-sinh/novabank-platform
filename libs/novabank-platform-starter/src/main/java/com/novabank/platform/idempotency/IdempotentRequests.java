package com.novabank.platform.idempotency;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Timestamp;
import java.time.Clock;
import java.util.HexFormat;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

/**
 * {@code Idempotency-Key} support for create operations: the same key always gives the same result,
 * so a client can safely retry after a timeout. The service must own this table:
 *
 * <pre>{@code
 * CREATE TABLE idempotency_keys (
 *     owner_id        UUID         NOT NULL,   -- the caller (JWT subject): keys are per caller
 *     idempotency_key VARCHAR(100) NOT NULL,
 *     request_hash    VARCHAR(64)  NOT NULL,
 *     response_body   JSONB,
 *     created_at      TIMESTAMP WITH TIME ZONE NOT NULL,
 *     PRIMARY KEY (owner_id, idempotency_key)
 * );
 * }</pre>
 *
 * <p>How it works, all inside the business transaction: the key is claimed with {@code INSERT ...
 * ON CONFLICT DO NOTHING}, the action runs, and its result is stored. A concurrent duplicate waits
 * on the primary key until the first transaction ends, then replays the stored result. If the
 * action fails, the transaction rolls back with the claim, so a failed request can be retried with
 * the same key. Only successful results are stored.
 */
public class IdempotentRequests {

  private final JdbcClient jdbcClient;
  private final JsonMapper jsonMapper;
  private final Clock clock;

  public IdempotentRequests(JdbcClient jdbcClient, JsonMapper jsonMapper, Clock clock) {
    this.jdbcClient = jdbcClient;
    this.jsonMapper = jsonMapper;
    this.clock = clock;
  }

  /**
   * Runs the action once per (owner, key).
   *
   * @param requestFingerprint the request's relevant content, e.g. {@code "CURRENT|INR"}; the same
   *     key with a different fingerprint is rejected
   * @throws IdempotencyKeyReusedException if the key was used for a different request
   */
  @Transactional(propagation = Propagation.MANDATORY)
  public <T> Result<T> execute(
      UUID ownerId,
      String idempotencyKey,
      String requestFingerprint,
      Class<T> resultType,
      Supplier<T> action) {
    Objects.requireNonNull(ownerId, "ownerId cannot be null");
    Objects.requireNonNull(idempotencyKey, "idempotencyKey cannot be null");
    String requestHash = sha256(Objects.requireNonNull(requestFingerprint));

    if (!claim(ownerId, idempotencyKey, requestHash)) {
      return replay(ownerId, idempotencyKey, requestHash, resultType);
    }

    T result = action.get();
    jdbcClient
        .sql(
            """
            UPDATE idempotency_keys SET response_body = CAST(:body AS jsonb)
            WHERE owner_id = :ownerId AND idempotency_key = :key
            """)
        .param("body", jsonMapper.writeValueAsString(result))
        .param("ownerId", ownerId)
        .param("key", idempotencyKey)
        .update();
    return new Result<>(result, false);
  }

  private boolean claim(UUID ownerId, String key, String requestHash) {
    return jdbcClient
            .sql(
                """
                INSERT INTO idempotency_keys (owner_id, idempotency_key, request_hash, created_at)
                VALUES (:ownerId, :key, :requestHash, :createdAt)
                ON CONFLICT (owner_id, idempotency_key) DO NOTHING
                """)
            .param("ownerId", ownerId)
            .param("key", key)
            .param("requestHash", requestHash)
            .param("createdAt", Timestamp.from(clock.instant()))
            .update()
        == 1;
  }

  private <T> Result<T> replay(UUID ownerId, String key, String requestHash, Class<T> type) {
    StoredRequest stored =
        jdbcClient
            .sql(
                """
                SELECT request_hash, response_body::text AS response_body
                FROM idempotency_keys
                WHERE owner_id = :ownerId AND idempotency_key = :key
                """)
            .param("ownerId", ownerId)
            .param("key", key)
            .query((rs, rowNum) ->
                new StoredRequest(rs.getString("request_hash"), rs.getString("response_body")))
            .single();
    if (!stored.requestHash().equals(requestHash)) {
      throw new IdempotencyKeyReusedException();
    }
    String body =
        Optional.ofNullable(stored.responseBody())
            // Claim and result are written in one transaction, so a visible claim has a result.
            .orElseThrow(() -> new IllegalStateException("Idempotency key without a result"));
    return new Result<>(jsonMapper.readValue(body, type), true);
  }

  static String sha256(String value) {
    try {
      return HexFormat.of()
          .formatHex(
              MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException ex) {
      throw new IllegalStateException("SHA-256 is required on every Java platform", ex);
    }
  }

  /**
   * @param value the action's result, or the stored result of the first request
   * @param replayed {@code true} if this is a repeat of an earlier request
   */
  public record Result<T>(T value, boolean replayed) {}

  private record StoredRequest(String requestHash, String responseBody) {}
}
