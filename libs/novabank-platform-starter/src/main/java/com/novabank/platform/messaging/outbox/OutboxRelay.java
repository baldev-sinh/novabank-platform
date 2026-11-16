package com.novabank.platform.messaging.outbox;

import com.novabank.platform.messaging.MessageHeaders;
import java.sql.Timestamp;
import java.time.Clock;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.NestedExceptionUtils;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.annotation.Transactional;

/**
 * Publishes outbox rows to Kafka in insert order, each to its own topic. Delivery is at-least-once:
 * if the service dies after Kafka acknowledged a message but before the row was marked, it is sent
 * again, so consumers must ignore duplicates (by the {@code eventId} header).
 */
public class OutboxRelay {

  private static final Logger LOGGER = LoggerFactory.getLogger(OutboxRelay.class);

  private final JdbcClient jdbcClient;
  private final KafkaTemplate<String, String> kafkaTemplate;
  private final OutboxProperties properties;
  private final Clock clock;

  public OutboxRelay(
      JdbcClient jdbcClient,
      KafkaTemplate<String, String> kafkaTemplate,
      OutboxProperties properties,
      Clock clock) {
    this.jdbcClient = jdbcClient;
    this.kafkaTemplate = kafkaTemplate;
    this.properties = properties;
    this.clock = clock;
  }

  /** First run one interval after startup, so the service is fully started before relaying. */
  @Scheduled(
      fixedDelayString = "${novabank.outbox.relay.poll-interval-ms:1000}",
      initialDelayString = "${novabank.outbox.relay.poll-interval-ms:1000}")
  @Transactional
  public void publishPending() {
    for (PendingMessage message : lockNextBatch()) {
      try {
        kafkaTemplate
            .send(toRecord(message))
            .get(properties.sendTimeout().toMillis(), TimeUnit.MILLISECONDS);
        markPublished(message.id());
      } catch (InterruptedException ex) {
        Thread.currentThread().interrupt();
        return;
      } catch (Exception ex) {
        // Stop here to keep order; messages already sent in this batch are still marked on commit.
        LOGGER.warn(
            "Publishing outbox message {} ({}) failed, will retry: {}",
            message.id(),
            message.eventType(),
            NestedExceptionUtils.getMostSpecificCause(ex).toString());
        return;
      }
    }
  }

  /**
   * Oldest unpublished messages, locked for this transaction. {@code SKIP LOCKED} lets several
   * service instances relay in parallel without ever picking the same row.
   */
  private List<PendingMessage> lockNextBatch() {
    return jdbcClient
        .sql(
            """
            SELECT id, topic, aggregate_id, event_type, payload::text AS payload
            FROM outbox_events
            WHERE published_at IS NULL
            ORDER BY position
            LIMIT :limit
            FOR UPDATE SKIP LOCKED
            """)
        .param("limit", properties.batchSize())
        .query((rs, rowNum) ->
            new PendingMessage(
                rs.getObject("id", UUID.class),
                rs.getString("topic"),
                rs.getObject("aggregate_id", UUID.class),
                rs.getString("event_type"),
                rs.getString("payload")))
        .list();
  }

  private void markPublished(UUID id) {
    jdbcClient
        .sql("UPDATE outbox_events SET published_at = :now WHERE id = :id")
        .param("now", Timestamp.from(clock.instant()))
        .param("id", id)
        .update();
  }

  private static ProducerRecord<String, String> toRecord(PendingMessage message) {
    ProducerRecord<String, String> producerRecord =
        new ProducerRecord<>(message.topic(), message.aggregateId().toString(), message.payload());
    producerRecord
        .headers()
        .add(MessageHeaders.EVENT_ID, MessageHeaders.bytes(message.id().toString()));
    producerRecord
        .headers()
        .add(MessageHeaders.EVENT_TYPE, MessageHeaders.bytes(message.eventType()));
    return producerRecord;
  }

  private record PendingMessage(
      UUID id, String topic, UUID aggregateId, String eventType, String payload) {}
}
