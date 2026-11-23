package com.novabank.notification.infrastructure.persistence;

import com.novabank.notification.application.port.NotificationStore;
import com.novabank.notification.domain.Notification;
import com.novabank.notification.domain.NotificationKind;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

@Repository
public class JdbcNotificationStore implements NotificationStore {

  private static final int MAX_ERROR_LENGTH = 500;
  private static final TypeReference<Map<String, String>> DETAILS = new TypeReference<>() {};

  private final JdbcClient jdbc;
  private final JsonMapper jsonMapper;

  public JdbcNotificationStore(JdbcClient jdbc, JsonMapper jsonMapper) {
    this.jdbc = jdbc;
    this.jsonMapper = jsonMapper;
  }

  @Override
  public boolean add(Notification n) {
    return jdbc.sql(
                """
                INSERT INTO notifications (id, event_id, kind, recipient_user_id,
                    recipient_account_id, details, status, attempts, next_attempt_at, created_at)
                VALUES (:id, :eventId, :kind, :userId, :accountId, CAST(:details AS JSONB),
                    'PENDING', 0, :createdAt, :createdAt)
                ON CONFLICT (event_id, kind) DO NOTHING
                """)
            .param("id", n.id())
            .param("eventId", n.eventId())
            .param("kind", n.kind().name())
            .param("userId", n.recipientUserId())
            .param("accountId", n.recipientAccountId())
            .param("details", jsonMapper.writeValueAsString(n.details()))
            .param("createdAt", Timestamp.from(n.createdAt()))
            .update()
        == 1;
  }

  /**
   * One statement: lock due rows other instances aren't holding ({@code SKIP LOCKED}), count the
   * attempt and push them past the lease; it commits as soon as it returns.
   */
  @Override
  @Transactional
  public List<Notification> claimDue(Instant now, Instant leaseUntil, int limit) {
    return jdbc.sql(
            """
            UPDATE notifications SET attempts = attempts + 1, next_attempt_at = :leaseUntil
            WHERE id IN (
                SELECT id FROM notifications
                WHERE status = 'PENDING' AND next_attempt_at <= :now
                ORDER BY next_attempt_at
                LIMIT :limit
                FOR UPDATE SKIP LOCKED)
            RETURNING id, event_id, kind, recipient_user_id, recipient_account_id, details::text,
                attempts, created_at
            """)
        .param("now", Timestamp.from(now))
        .param("leaseUntil", Timestamp.from(leaseUntil))
        .param("limit", limit)
        .query(this::notification)
        .list();
  }

  @Override
  public void markSent(UUID id, Instant sentAt) {
    jdbc.sql(
            "UPDATE notifications SET status = 'SENT', sent_at = :sentAt, last_error = NULL"
                + " WHERE id = :id")
        .param("id", id)
        .param("sentAt", Timestamp.from(sentAt))
        .update();
  }

  @Override
  public void retryLater(UUID id, Instant nextAttemptAt, String error) {
    jdbc.sql("UPDATE notifications SET next_attempt_at = :next, last_error = :error WHERE id = :id")
        .param("id", id)
        .param("next", Timestamp.from(nextAttemptAt))
        .param("error", truncate(error))
        .update();
  }

  @Override
  public void markFailed(UUID id, String error) {
    jdbc.sql("UPDATE notifications SET status = 'FAILED', last_error = :error WHERE id = :id")
        .param("id", id)
        .param("error", truncate(error))
        .update();
  }

  private Notification notification(ResultSet rs, int row) throws SQLException {
    return new Notification(
        rs.getObject("id", UUID.class),
        rs.getObject("event_id", UUID.class),
        NotificationKind.valueOf(rs.getString("kind")),
        rs.getObject("recipient_user_id", UUID.class),
        rs.getObject("recipient_account_id", UUID.class),
        jsonMapper.readValue(rs.getString("details"), DETAILS),
        rs.getInt("attempts"),
        rs.getTimestamp("created_at").toInstant());
  }

  private static String truncate(String error) {
    if (error == null) {
      return null;
    }
    return error.length() <= MAX_ERROR_LENGTH ? error : error.substring(0, MAX_ERROR_LENGTH);
  }
}
