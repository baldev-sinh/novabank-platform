package com.novabank.auth.infrastructure.messaging.outbox;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.NestedExceptionUtils;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.annotation.Transactional;

/**
 * Publishes outbox rows to Kafka, oldest first. Delivery is at-least-once: if the service dies
 * after Kafka acknowledged an event but before the row was marked, the event is sent again, so
 * consumers must ignore duplicates (by the {@code eventId} header).
 */
public class OutboxRelay {

  static final String EVENT_ID_HEADER = "eventId";
  static final String EVENT_TYPE_HEADER = "eventType";

  private static final Logger LOGGER = LoggerFactory.getLogger(OutboxRelay.class);

  private final SpringDataOutboxRepository repository;
  private final KafkaTemplate<String, String> kafkaTemplate;
  private final OutboxProperties properties;
  private final Clock clock;

  public OutboxRelay(
      SpringDataOutboxRepository repository,
      KafkaTemplate<String, String> kafkaTemplate,
      OutboxProperties properties,
      Clock clock) {
    this.repository = repository;
    this.kafkaTemplate = kafkaTemplate;
    this.properties = properties;
    this.clock = clock;
  }

  @Scheduled(fixedDelayString = "${outbox.relay.poll-interval-ms:1000}")
  @Transactional
  public void publishPending() {
    List<OutboxEventEntity> batch = repository.lockNextBatch(properties.batchSize());
    for (OutboxEventEntity event : batch) {
      try {
        kafkaTemplate
            .send(toRecord(event))
            .get(properties.sendTimeout().toMillis(), TimeUnit.MILLISECONDS);
        event.markPublished(clock.instant());
      } catch (InterruptedException ex) {
        Thread.currentThread().interrupt();
        return;
      } catch (Exception ex) {
        // Stop here to keep order; events already sent in this batch are still marked on commit.
        LOGGER.warn(
            "Publishing outbox event {} ({}) failed, will retry: {}",
            event.getId(),
            event.getEventType(),
            NestedExceptionUtils.getMostSpecificCause(ex).toString());
        return;
      }
    }
  }

  private ProducerRecord<String, String> toRecord(OutboxEventEntity event) {
    ProducerRecord<String, String> producerRecord =
        new ProducerRecord<>(
            properties.topic(), event.getAggregateId().toString(), event.getPayload());
    producerRecord.headers().add(EVENT_ID_HEADER, bytes(event.getId().toString()));
    producerRecord.headers().add(EVENT_TYPE_HEADER, bytes(event.getEventType()));
    return producerRecord;
  }

  private static byte[] bytes(String value) {
    return value.getBytes(StandardCharsets.UTF_8);
  }
}
