package com.novabank.platform.messaging.outbox;

import static org.assertj.core.api.Assertions.assertThat;

import com.novabank.platform.messaging.MessageHeaders;
import com.novabank.platform.support.PostgresTestDatabase;
import java.sql.Connection;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.kafka.KafkaContainer;
import tools.jackson.databind.json.JsonMapper;

/**
 * The relay's guarantees against real PostgreSQL and Kafka: insert order, key and headers, marked
 * only after Kafka acknowledged, retried after a failure, and never sent twice by parallel relays.
 */
class OutboxRelayIT {

  private static final KafkaContainer KAFKA = new KafkaContainer("apache/kafka:4.4.0");

  private static KafkaTemplate<String, String> kafkaTemplate;

  private final JsonMapper jsonMapper = JsonMapper.builder().build();
  private final JdbcClient jdbc = PostgresTestDatabase.jdbcClient();
  private final TransactionTemplate transactions = PostgresTestDatabase.transactions();
  private final OutboxWriter writer = new OutboxWriter(jdbc, jsonMapper, OutboxTracing.NONE);

  @BeforeAll
  static void startKafka() {
    KAFKA.start();
    kafkaTemplate = template(KAFKA.getBootstrapServers(), Duration.ofSeconds(10));
  }

  @AfterAll
  static void closeProducer() {
    kafkaTemplate.destroy();
  }

  @Test
  @DisplayName(
      "Messages go out in insert order, keyed by aggregate, with eventId/eventType headers")
  void publishesInOrderWithKeyAndHeaders() {
    String topic = newTopic();
    UUID aggregate = UUID.randomUUID();
    List<UUID> ids = List.of(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());
    transactions.executeWithoutResult(
        status -> ids.forEach(id -> writer.append(record(id, topic, aggregate))));

    relay(kafkaTemplate).run();

    List<ConsumerRecord<String, String>> received = consume(topic, 3);
    assertThat(received).extracting(r -> MessageHeaders.eventId(r)).containsExactlyElementsOf(ids);
    assertThat(received)
        .allSatisfy(r -> {
          assertThat(r.key()).isEqualTo(aggregate.toString());
          assertThat(MessageHeaders.eventType(r)).isEqualTo("Thing.v1");
          // jsonb normalises the stored payload's whitespace.
          assertThat(r.value()).isEqualToIgnoringWhitespace("{\"name\":\"thing\"}");
        });
    assertThat(unpublished(ids)).isZero();
  }

  @Test
  @DisplayName("Already published rows are not sent again")
  void publishedRowsAreNotResent() {
    String topic = newTopic();
    UUID id = UUID.randomUUID();
    transactions.executeWithoutResult(
        status -> writer.append(record(id, topic, UUID.randomUUID())));
    Relay relay = relay(kafkaTemplate);

    relay.run();
    relay.run();

    assertThat(consume(topic, 1)).hasSize(1);
  }

  @Test
  @DisplayName("If Kafka can't be reached the row stays unpublished, and a later run delivers it")
  void failedSendIsRetried() {
    String topic = newTopic();
    UUID id = UUID.randomUUID();
    transactions.executeWithoutResult(
        status -> writer.append(record(id, topic, UUID.randomUUID())));
    KafkaTemplate<String, String> unreachable = template("localhost:1", Duration.ofSeconds(2));
    try {
      relay(unreachable).run();
    } finally {
      unreachable.destroy();
    }
    assertThat(unpublished(List.of(id))).isOne();

    relay(kafkaTemplate).run();

    assertThat(unpublished(List.of(id))).isZero();
    assertThat(consume(topic, 1)).hasSize(1);
  }

  @Test
  @DisplayName(
      "Rows locked by another relay are skipped, never sent twice (FOR UPDATE SKIP LOCKED)")
  void lockedRowsAreSkipped() throws Exception {
    String topic = newTopic();
    UUID id = UUID.randomUUID();
    transactions.executeWithoutResult(
        status -> writer.append(record(id, topic, UUID.randomUUID())));

    // Another instance's relay transaction holds the row.
    try (Connection other = PostgresTestDatabase.dataSource().getConnection()) {
      other.setAutoCommit(false);
      try (var statement = other.createStatement()) {
        statement.execute("SELECT id FROM outbox_events WHERE id = '" + id + "' FOR UPDATE");
      }
      relay(kafkaTemplate).run();
      assertThat(unpublished(List.of(id))).isOne();
      other.rollback();
    }

    relay(kafkaTemplate).run();
    assertThat(unpublished(List.of(id))).isZero();
    assertThat(consume(topic, 1)).hasSize(1);
  }

  /** The relay is @Transactional; without a Spring proxy the template provides the transaction. */
  private Relay relay(KafkaTemplate<String, String> template) {
    OutboxProperties properties =
        new OutboxProperties(
            true, 100, Duration.ofSeconds(5), new OutboxProperties.Relay(true, 1000));
    OutboxRelay relay =
        new OutboxRelay(
            jdbc, template, properties, Clock.systemUTC(), OutboxTracing.NONE, jsonMapper);
    return () -> transactions.executeWithoutResult(status -> relay.publishPending());
  }

  private interface Relay {
    void run();
  }

  private static OutboxRecord record(UUID id, String topic, UUID aggregate) {
    return new OutboxRecord(
        id, topic, "Thing.v1", "Thing", aggregate, Map.of("name", "thing"), Instant.now());
  }

  private long unpublished(List<UUID> ids) {
    return jdbc.sql(
            "SELECT count(*) FROM outbox_events WHERE published_at IS NULL AND id IN (:ids)")
        .param("ids", ids)
        .query(Long.class)
        .single();
  }

  private static String newTopic() {
    return "outbox-it-" + UUID.randomUUID();
  }

  private static KafkaTemplate<String, String> template(String bootstrap, Duration timeout) {
    int timeoutMs = (int) timeout.toMillis();
    return new KafkaTemplate<>(
        new DefaultKafkaProducerFactory<String, String>(
            Map.of(
                ProducerConfig.BOOTSTRAP_SERVERS_CONFIG,
                bootstrap,
                ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG,
                StringSerializer.class,
                ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG,
                StringSerializer.class,
                ProducerConfig.MAX_BLOCK_MS_CONFIG,
                timeoutMs,
                // Kafka requires delivery.timeout.ms >= linger.ms + request.timeout.ms.
                ProducerConfig.REQUEST_TIMEOUT_MS_CONFIG,
                timeoutMs / 2,
                ProducerConfig.DELIVERY_TIMEOUT_MS_CONFIG,
                timeoutMs)));
  }

  /** Reads the topic from the beginning until {@code expected} records arrived or 15 s passed. */
  private static List<ConsumerRecord<String, String>> consume(String topic, int expected) {
    try (KafkaConsumer<String, String> consumer =
        new KafkaConsumer<>(
            Map.of(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG,
                KAFKA.getBootstrapServers(),
                ConsumerConfig.GROUP_ID_CONFIG,
                "it-" + UUID.randomUUID(),
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG,
                "earliest"),
            new StringDeserializer(),
            new StringDeserializer())) {
      consumer.subscribe(List.of(topic));
      List<ConsumerRecord<String, String>> received = new ArrayList<>();
      long deadline = System.nanoTime() + Duration.ofSeconds(15).toNanos();
      while (received.size() < expected && System.nanoTime() < deadline) {
        consumer.poll(Duration.ofMillis(500)).forEach(received::add);
      }
      // A short extra poll catches anything sent twice.
      consumer.poll(Duration.ofSeconds(1)).forEach(received::add);
      return received;
    }
  }
}
