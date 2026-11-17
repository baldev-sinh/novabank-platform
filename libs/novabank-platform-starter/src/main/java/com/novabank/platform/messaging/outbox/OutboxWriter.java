package com.novabank.platform.messaging.outbox;

import java.sql.Timestamp;
import java.util.Map;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

/**
 * Writes messages to the transactional outbox. The service must own this table (Flyway):
 *
 * <pre>{@code
 * CREATE TABLE outbox_events (
 *     id             UUID         PRIMARY KEY,
 *     position       BIGINT       GENERATED ALWAYS AS IDENTITY,
 *     topic          VARCHAR(255) NOT NULL,
 *     aggregate_type VARCHAR(50)  NOT NULL,
 *     aggregate_id   UUID         NOT NULL,
 *     event_type     VARCHAR(100) NOT NULL,
 *     payload        JSONB        NOT NULL,
 *     created_at     TIMESTAMP WITH TIME ZONE NOT NULL,
 *     published_at   TIMESTAMP WITH TIME ZONE,
 *     trace_headers  JSONB  -- trace context at write time (OutboxTracing), may be null
 * );
 * CREATE INDEX idx_outbox_events_unpublished ON outbox_events (position)
 *     WHERE published_at IS NULL;
 * }</pre>
 *
 * Plain JDBC rather than a JPA entity, so services don't have to scan this library's entities; it
 * still joins the surrounding JPA transaction (same connection).
 */
public class OutboxWriter {

  private final JdbcClient jdbcClient;
  private final JsonMapper jsonMapper;
  private final OutboxTracing tracing;

  public OutboxWriter(JdbcClient jdbcClient, JsonMapper jsonMapper, OutboxTracing tracing) {
    this.jdbcClient = jdbcClient;
    this.jsonMapper = jsonMapper;
    this.tracing = tracing;
  }

  /**
   * MANDATORY: must run inside the transaction that makes the business change, so the change and
   * its message are committed together or not at all. Fails fast otherwise.
   */
  @Transactional(propagation = Propagation.MANDATORY)
  public void append(OutboxRecord message) {
    jdbcClient
        .sql(
            """
            INSERT INTO outbox_events
                (id, topic, aggregate_type, aggregate_id, event_type, payload, created_at,
                 trace_headers)
            VALUES
                (:id, :topic, :aggregateType, :aggregateId, :eventType, CAST(:payload AS jsonb),
                 :createdAt, CAST(:traceHeaders AS jsonb))
            """)
        .param("id", message.eventId())
        .param("topic", message.topic())
        .param("aggregateType", message.aggregateType())
        .param("aggregateId", message.aggregateId())
        .param("eventType", message.eventType())
        .param("payload", jsonMapper.writeValueAsString(message.payload()))
        .param("createdAt", Timestamp.from(message.occurredAt()))
        .param("traceHeaders", traceHeaders())
        .update();
  }

  private String traceHeaders() {
    Map<String, String> headers = tracing.capture();
    return headers.isEmpty() ? null : jsonMapper.writeValueAsString(headers);
  }
}
