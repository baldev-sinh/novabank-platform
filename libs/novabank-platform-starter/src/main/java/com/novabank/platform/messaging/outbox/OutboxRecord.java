package com.novabank.platform.messaging.outbox;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * One message to publish.
 *
 * @param eventId unique message id, sent as the {@code eventId} header (consumers deduplicate on
 *     it)
 * @param topic destination topic
 * @param eventType versioned type name, e.g. {@code UserRegistered.v1}
 * @param aggregateType e.g. {@code User}, {@code Payment}
 * @param aggregateId Kafka key: all messages of one aggregate go to one partition, in order
 * @param payload serialized to JSON with the service's JsonMapper
 * @param occurredAt when the fact happened
 */
public record OutboxRecord(
    UUID eventId,
    String topic,
    String eventType,
    String aggregateType,
    UUID aggregateId,
    Object payload,
    Instant occurredAt) {

  public OutboxRecord {
    Objects.requireNonNull(eventId, "eventId cannot be null");
    Objects.requireNonNull(topic, "topic cannot be null");
    Objects.requireNonNull(eventType, "eventType cannot be null");
    Objects.requireNonNull(aggregateType, "aggregateType cannot be null");
    Objects.requireNonNull(aggregateId, "aggregateId cannot be null");
    Objects.requireNonNull(payload, "payload cannot be null");
    Objects.requireNonNull(occurredAt, "occurredAt cannot be null");
  }
}
