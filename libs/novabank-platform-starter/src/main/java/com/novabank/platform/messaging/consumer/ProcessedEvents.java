package com.novabank.platform.messaging.consumer;

import java.sql.Timestamp;
import java.time.Clock;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Registry of handled message ids, for idempotent consumers. Record the id in the same transaction
 * as the message's effect: either both commit or neither, so a redelivered message is applied
 * exactly once. The service must own this table (Flyway):
 *
 * <pre>{@code
 * CREATE TABLE processed_events (
 *     event_id     UUID         PRIMARY KEY,
 *     event_type   VARCHAR(100) NOT NULL,
 *     processed_at TIMESTAMP WITH TIME ZONE NOT NULL
 * );
 * }</pre>
 */
public class ProcessedEvents {

  private final JdbcClient jdbcClient;
  private final Clock clock;

  public ProcessedEvents(JdbcClient jdbcClient, Clock clock) {
    this.jdbcClient = jdbcClient;
    this.clock = clock;
  }

  /**
   * {@code ON CONFLICT DO NOTHING}: of two concurrent deliveries only one wins, and nothing throws.
   *
   * @return {@code false} if the message was already processed (a duplicate delivery)
   */
  @Transactional(propagation = Propagation.MANDATORY)
  public boolean markProcessed(UUID eventId, String eventType) {
    int inserted =
        jdbcClient
            .sql(
                """
                INSERT INTO processed_events (event_id, event_type, processed_at)
                VALUES (:eventId, :eventType, :processedAt)
                ON CONFLICT (event_id) DO NOTHING
                """)
            .param("eventId", eventId)
            .param("eventType", eventType)
            .param("processedAt", Timestamp.from(clock.instant()))
            .update();
    return inserted == 1;
  }
}
