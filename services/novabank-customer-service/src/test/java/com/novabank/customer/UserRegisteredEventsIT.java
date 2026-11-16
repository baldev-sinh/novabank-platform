package com.novabank.customer;

import static com.novabank.customer.support.KafkaTestcontainersConfiguration.USER_EVENTS_TOPIC;
import static org.assertj.core.api.Assertions.assertThat;

import com.novabank.customer.support.KafkaTestcontainersConfiguration;
import com.novabank.customer.support.TestcontainersConfiguration;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.kafka.autoconfigure.KafkaConnectionDetails;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;

/**
 * The consumer side of UserRegistered against a real broker and database: applied once despite
 * redelivery, poison records go to the dead-letter topic without blocking the partition.
 */
@SpringBootTest(
    properties = {
      "security.jwt.jwk-set-uri=http://localhost:1/not-used-in-this-test",
      "security.jwt.issuer=http://auth.test",
      "security.jwt.audience=novabank-api",
      "events.dead-letter-replication-factor=1"
    })
@Import({TestcontainersConfiguration.class, KafkaTestcontainersConfiguration.class})
class UserRegisteredEventsIT {

  private static final String DEAD_LETTER_TOPIC = USER_EVENTS_TOPIC + "-dlt";
  private static final Duration TIMEOUT = Duration.ofSeconds(30);

  @Autowired private KafkaTemplate<Object, Object> kafkaTemplate;
  @Autowired private KafkaConnectionDetails kafkaConnectionDetails;
  @Autowired private JdbcTemplate jdbcTemplate;

  @Test
  @DisplayName("A UserRegistered event is stored as a known user and marked processed")
  void storesKnownUser() {
    UUID eventId = UUID.randomUUID();
    UUID userId = UUID.randomUUID();

    send(userId.toString(), null, "UserRegistered.v1", eventId, payload(eventId, userId, "a@x.io"));

    Awaitility.await().atMost(TIMEOUT).until(() -> emailOf(userId) != null);
    assertThat(emailOf(userId)).isEqualTo("a@x.io");
    assertThat(processedCount(eventId)).isOne();
  }

  @Test
  @DisplayName("A redelivered event (same eventId) is applied only once")
  void appliesDuplicateOnce() {
    UUID eventId = UUID.randomUUID();
    UUID userId = UUID.randomUUID();
    String key = userId.toString();

    send(key, null, "UserRegistered.v1", eventId, payload(eventId, userId, "first@x.io"));
    send(key, null, "UserRegistered.v1", eventId, payload(eventId, userId, "second@x.io"));
    // Same key, same partition: once this marker is stored, both copies above were consumed.
    UUID markerEvent = UUID.randomUUID();
    UUID markerUser = UUID.randomUUID();
    send(key, null, "UserRegistered.v1", markerEvent, payload(markerEvent, markerUser, "m@x.io"));

    Awaitility.await().atMost(TIMEOUT).until(() -> emailOf(markerUser) != null);
    assertThat(emailOf(userId)).isEqualTo("first@x.io");
    assertThat(processedCount(eventId)).isOne();
  }

  @Test
  @DisplayName(
      "A poison record goes to the dead-letter topic on the same partition; unknown types don't")
  void poisonRecordGoesToDeadLetterTopic() {
    String key = UUID.randomUUID().toString();
    send(key, 1, "SomethingNew.v1", UUID.randomUUID(), "unknown-type-body");
    send(key, 1, "UserRegistered.v1", UUID.randomUUID(), "poison-body");
    // The partition isn't blocked: a valid event after the poison one is still applied.
    UUID eventId = UUID.randomUUID();
    UUID userId = UUID.randomUUID();
    send(key, 1, "UserRegistered.v1", eventId, payload(eventId, userId, "after@x.io"));

    List<ConsumerRecord<String, String>> deadLetters = awaitDeadLetter("poison-body");

    ConsumerRecord<String, String> poison = deadLetters.getLast();
    assertThat(poison.partition()).isEqualTo(1);
    // The listener's exception is wrapped in ListenerExecutionFailedException; ours is the cause.
    assertThat(header(poison, "kafka_dlt-exception-cause-fqcn"))
        .isEqualTo("com.novabank.customer.infrastructure.messaging.InvalidEventException");
    // Not retried: dead-lettered before the first 1 s backoff would have elapsed.
    long originalTimestamp =
        ByteBuffer.wrap(poison.headers().lastHeader("kafka_dlt-original-timestamp").value())
            .getLong();
    assertThat(poison.timestamp() - originalTimestamp).isLessThan(1000);
    assertThat(deadLetters).noneMatch(r -> "unknown-type-body".equals(r.value()));
    Awaitility.await().atMost(TIMEOUT).until(() -> emailOf(userId) != null);
  }

  private void send(String key, Integer partition, String type, UUID eventId, String value) {
    ProducerRecord<Object, Object> producerRecord =
        new ProducerRecord<>(USER_EVENTS_TOPIC, partition, key, value);
    producerRecord.headers().add("eventType", type.getBytes(StandardCharsets.UTF_8));
    producerRecord.headers().add("eventId", eventId.toString().getBytes(StandardCharsets.UTF_8));
    kafkaTemplate.send(producerRecord).join();
  }

  /** Same shape as the auth service's UserRegisteredEvent. */
  private static String payload(UUID eventId, UUID userId, String email) {
    return "{\"eventId\":\"%s\",\"userId\":\"%s\",\"email\":\"%s\",\"occurredAt\":\"2026-11-16T10:00:00Z\"}"
        .formatted(eventId, userId, email);
  }

  private String emailOf(UUID userId) {
    return jdbcTemplate
        .queryForList("SELECT email FROM known_users WHERE user_id = ?", String.class, userId)
        .stream()
        .findFirst()
        .orElse(null);
  }

  private Integer processedCount(UUID eventId) {
    return jdbcTemplate.queryForObject(
        "SELECT count(*) FROM processed_events WHERE event_id = ?", Integer.class, eventId);
  }

  /** Reads the dead-letter topic until a record with this value arrives; returns all seen. */
  private List<ConsumerRecord<String, String>> awaitDeadLetter(String value) {
    Map<String, Object> config =
        Map.of(
            ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG,
            String.join(",", kafkaConnectionDetails.getBootstrapServers()),
            ConsumerConfig.GROUP_ID_CONFIG,
            "dlt-reader-" + UUID.randomUUID(),
            ConsumerConfig.AUTO_OFFSET_RESET_CONFIG,
            "earliest",
            ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG,
            StringDeserializer.class,
            ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG,
            StringDeserializer.class);
    List<ConsumerRecord<String, String>> seen = new ArrayList<>();
    try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(config)) {
      consumer.subscribe(List.of(DEAD_LETTER_TOPIC));
      Awaitility.await()
          .atMost(TIMEOUT)
          .until(() -> {
            consumer.poll(Duration.ofMillis(500)).forEach(seen::add);
            return seen.stream().anyMatch(r -> value.equals(r.value()));
          });
    }
    return seen;
  }

  private static String header(ConsumerRecord<String, String> consumed, String name) {
    return new String(consumed.headers().lastHeader(name).value(), StandardCharsets.UTF_8);
  }
}
