package com.novabank.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.novabank.audit.application.service.RecordAuditEventService;
import com.novabank.audit.application.service.VerifyChainService;
import com.novabank.audit.support.AuditTestcontainers;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.kafka.autoconfigure.KafkaConnectionDetails;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/**
 * The audit service against a real broker and database: every business event is chained once, the
 * chain verifies, tampering is found, history can't be changed through SQL, and only compliance and
 * administrators can read it.
 */
@SpringBootTest(
    properties = {
      "security.jwt.jwk-set-uri=http://localhost:1/not-used-in-this-test",
      "security.jwt.issuer=http://auth.test",
      "security.jwt.audience=novabank-api",
      "audit.dead-letter-replication-factor=1",
      // The scheduled check would race the tamper test; it is called directly instead.
      "audit.verify.initial-delay=1h"
    })
@AutoConfigureMockMvc
@Import(AuditTestcontainers.class)
class AuditLogIT {

  private static final Duration TIMEOUT = Duration.ofSeconds(30);

  @Autowired private KafkaTemplate<Object, Object> kafkaTemplate;
  @Autowired private KafkaConnectionDetails kafkaConnectionDetails;
  @Autowired private JdbcTemplate jdbcTemplate;
  @Autowired private MockMvc mockMvc;
  @Autowired private RecordAuditEventService recordEvent;
  @Autowired private VerifyChainService verifyChain;

  /** A fresh random key per run: nothing secret is in the repository. */
  @DynamicPropertySource
  static void chainKey(DynamicPropertyRegistry registry) {
    byte[] key = new byte[32];
    new java.security.SecureRandom().nextBytes(key);
    registry.add("audit.chain-key", () -> Base64.getEncoder().encodeToString(key));
  }

  @Test
  @DisplayName("Events from every topic are chained once each, even when delivered twice")
  void recordsEachEventOnce() throws Exception {
    String account = UUID.randomUUID().toString();
    UUID opened = UUID.randomUUID();
    UUID payment = UUID.randomUUID();

    send(
        "account.events.v1",
        account,
        "AccountOpened.v1",
        opened,
        "{\"accountId\":\"" + account + "\"}");
    send(
        "account.events.v1",
        account,
        "AccountOpened.v1",
        opened,
        "{\"accountId\":\"" + account + "\"}");
    send(
        "payment.events.v1",
        payment.toString(),
        "PaymentCompleted.v1",
        UUID.randomUUID(),
        "{\"amount\":\"10.00\"}");
    // Not a business fact: skipped.
    send("ledger.events.v1", account, "LedgerBalanceSnapshot.v1", UUID.randomUUID(), "{}");
    UUID marker = UUID.randomUUID();
    send(
        "account.events.v1",
        account,
        "AccountActivated.v1",
        marker,
        "{\"accountId\":\"" + account + "\"}");

    Awaitility.await().atMost(TIMEOUT).until(() -> recorded(marker));
    Awaitility.await().atMost(TIMEOUT).until(() -> countFor(payment.toString()) == 1);
    assertThat(
            jdbcTemplate.queryForList(
                "SELECT event_type FROM audit_entries WHERE record_key = ? ORDER BY seq",
                String.class,
                account))
        .containsExactly("AccountOpened.v1", "AccountActivated.v1");

    mockMvc
        .perform(
            get("/api/v1/audit/entries").param("key", account).with(staff("COMPLIANCE_OFFICER")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.entries.length()").value(2))
        .andExpect(jsonPath("$.entries[0].eventType").value("AccountActivated.v1"))
        .andExpect(jsonPath("$.entries[1].payload.accountId").value(account))
        .andExpect(jsonPath("$.entries[0].hash").isNotEmpty());

    mockMvc
        .perform(get("/api/v1/audit/verify").with(staff("ADMIN")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.valid").value(true));
  }

  @Test
  @DisplayName("An entry changed behind the service's back is found by the check")
  void tamperingIsFound() throws Exception {
    UUID eventId = UUID.randomUUID();
    String key = UUID.randomUUID().toString();
    send("payment.events.v1", key, "PaymentCompleted.v1", eventId, "{\"amount\":\"10.00\"}");
    Awaitility.await().atMost(TIMEOUT).until(() -> recorded(eventId));
    long seq =
        jdbcTemplate.queryForObject(
            "SELECT seq FROM audit_entries WHERE event_id = ?", Long.class, eventId);

    // What someone with database access could do: switch the guard off and edit the amount.
    tamper(eventId, "{\"amount\":\"99999.00\"}");
    try {
      mockMvc
          .perform(get("/api/v1/audit/verify").with(staff("COMPLIANCE_OFFICER")))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.valid").value(false))
          .andExpect(jsonPath("$.brokenAtSeq").value(seq))
          .andExpect(jsonPath("$.problem").value("Content doesn't match its hash"));
    } finally {
      tamper(eventId, "{\"amount\":\"10.00\"}");
    }
    mockMvc
        .perform(get("/api/v1/audit/verify").with(staff("COMPLIANCE_OFFICER")))
        .andExpect(jsonPath("$.valid").value(true));
  }

  @Test
  @DisplayName("Concurrent appends still form one unbroken chain with no gaps")
  void concurrentAppendsKeepTheChain() throws Exception {
    ExecutorService pool = Executors.newFixedThreadPool(8);
    try {
      List<Future<Boolean>> results = new ArrayList<>();
      for (int i = 0; i < 200; i++) {
        UUID eventId = UUID.randomUUID();
        results.add(
            pool.submit(() ->
                recordEvent.record(
                    eventId,
                    "PaymentCompleted.v1",
                    "payment.events.v1",
                    "concurrent",
                    Instant.now(),
                    "{}")));
      }
      for (Future<Boolean> result : results) {
        assertThat(result.get(30, TimeUnit.SECONDS)).isTrue();
      }
    } finally {
      pool.shutdownNow();
    }

    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT max(seq) = count(*) FROM audit_entries", Boolean.class))
        .isTrue();
    assertThat(verifyChain.verify().valid()).isTrue();
  }

  @Test
  @DisplayName("Search filters by key, type and time, pages with nextBefore; non-JSON stays text")
  void searchFiltersAndPages() throws Exception {
    String key = UUID.randomUUID().toString();
    UUID last = UUID.randomUUID();
    send("payment.events.v1", key, "PaymentFailed.v1", UUID.randomUUID(), "not json");
    send("payment.events.v1", key, "PaymentCompleted.v1", UUID.randomUUID(), "{}");
    send("payment.events.v1", key, "PaymentCompleted.v1", last, "{}");
    Awaitility.await().atMost(TIMEOUT).until(() -> countFor(key) == 3);

    String firstPage =
        mockMvc
            .perform(
                get("/api/v1/audit/entries")
                    .param("key", key)
                    .param("eventType", "PaymentCompleted.v1")
                    .param("from", "2000-01-01T00:00:00Z")
                    .param("to", "2999-01-01T00:00:00Z")
                    .param("limit", "1")
                    .with(staff("ADMIN")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.entries.length()").value(1))
            .andExpect(jsonPath("$.entries[0].eventId").value(last.toString()))
            .andExpect(jsonPath("$.nextBefore").isNumber())
            .andReturn()
            .getResponse()
            .getContentAsString();
    long nextBefore = ((Number) JsonPath.read(firstPage, "$.nextBefore")).longValue();

    mockMvc
        .perform(
            get("/api/v1/audit/entries")
                .param("key", key)
                .param("eventType", "PaymentCompleted.v1")
                .param("before", Long.toString(nextBefore))
                .param("limit", "5")
                .with(staff("ADMIN")))
        .andExpect(jsonPath("$.entries.length()").value(1))
        .andExpect(jsonPath("$.nextBefore").doesNotExist());
    mockMvc
        .perform(
            get("/api/v1/audit/entries")
                .param("key", key)
                .param("eventType", "PaymentFailed.v1")
                .with(staff("ADMIN")))
        .andExpect(jsonPath("$.entries[0].payload").value("not json"));
    mockMvc
        .perform(
            get("/api/v1/audit/entries")
                .param("key", key)
                .param("to", "2000-01-01T00:00:00Z")
                .with(staff("ADMIN")))
        .andExpect(jsonPath("$.entries.length()").value(0));
  }

  @Test
  @DisplayName("History can't be changed or deleted through SQL")
  void appendOnly() {
    UUID eventId = UUID.randomUUID();
    send("customer.events.v1", UUID.randomUUID().toString(), "CustomerVerified.v1", eventId, "{}");
    Awaitility.await().atMost(TIMEOUT).until(() -> recorded(eventId));

    assertThatThrownBy(() ->
            jdbcTemplate.update(
                "UPDATE audit_entries SET payload = '{}' WHERE event_id = ?", eventId))
        .isInstanceOf(DataAccessException.class)
        .hasMessageContaining("append-only");
    assertThatThrownBy(
            () -> jdbcTemplate.update("DELETE FROM audit_entries WHERE event_id = ?", eventId))
        .isInstanceOf(DataAccessException.class)
        .hasMessageContaining("append-only");
    assertThatThrownBy(() -> jdbcTemplate.update("DELETE FROM audit_chain_head"))
        .isInstanceOf(DataAccessException.class);
  }

  @Test
  @DisplayName("Only compliance and administrators read the log; nobody writes through the API")
  void access() throws Exception {
    mockMvc.perform(get("/api/v1/audit/entries")).andExpect(status().isUnauthorized());
    mockMvc
        .perform(get("/api/v1/audit/entries").with(staff("CUSTOMER")))
        .andExpect(status().isForbidden());
    mockMvc
        .perform(get("/api/v1/audit/verify").with(staff("OPERATIONS")))
        .andExpect(status().isForbidden());
    mockMvc
        .perform(get("/api/v1/audit/entries").param("limit", "500").with(staff("ADMIN")))
        .andExpect(status().isBadRequest());
  }

  @Test
  @DisplayName("A message without an eventId goes to this service's own dead-letter topic")
  void invalidMessageIsDeadLettered() {
    ProducerRecord<Object, Object> record =
        new ProducerRecord<>("ledger.events.v1", "k", "no-event-id");
    record
        .headers()
        .add("eventType", "LedgerTransactionPosted.v1".getBytes(StandardCharsets.UTF_8));
    kafkaTemplate.send(record).join();
    ProducerRecord<Object, Object> untyped =
        new ProducerRecord<>("ledger.events.v1", "k", "no-type");
    untyped.headers().add("eventId", UUID.randomUUID().toString().getBytes(StandardCharsets.UTF_8));
    kafkaTemplate.send(untyped).join();

    try (KafkaConsumer<String, String> consumer = deadLetterConsumer()) {
      consumer.subscribe(List.of("ledger.events.v1.audit-service-dlt"));
      List<String> values = new ArrayList<>();
      Awaitility.await()
          .atMost(TIMEOUT)
          .until(() -> {
            consumer.poll(Duration.ofMillis(500)).forEach(r -> values.add(r.value()));
            return values.containsAll(List.of("no-event-id", "no-type"));
          });
    }
  }

  private void send(String topic, String key, String type, UUID eventId, String value) {
    ProducerRecord<Object, Object> record = new ProducerRecord<>(topic, key, value);
    record.headers().add("eventType", type.getBytes(StandardCharsets.UTF_8));
    record.headers().add("eventId", eventId.toString().getBytes(StandardCharsets.UTF_8));
    kafkaTemplate.send(record).join();
  }

  private boolean recorded(UUID eventId) {
    return jdbcTemplate.queryForObject(
            "SELECT count(*) FROM audit_entries WHERE event_id = ?", Integer.class, eventId)
        == 1;
  }

  private int countFor(String key) {
    return jdbcTemplate.queryForObject(
        "SELECT count(*) FROM audit_entries WHERE record_key = ?", Integer.class, key);
  }

  private void tamper(UUID eventId, String payload) {
    jdbcTemplate.execute(
        "ALTER TABLE audit_entries DISABLE TRIGGER audit_entries_no_update_or_delete");
    try {
      jdbcTemplate.update(
          "UPDATE audit_entries SET payload = ? WHERE event_id = ?", payload, eventId);
    } finally {
      jdbcTemplate.execute(
          "ALTER TABLE audit_entries ENABLE TRIGGER audit_entries_no_update_or_delete");
    }
  }

  private static RequestPostProcessor staff(String role) {
    return jwt()
        .jwt(j -> j.subject(UUID.randomUUID().toString()))
        .authorities(new SimpleGrantedAuthority("ROLE_" + role));
  }

  private KafkaConsumer<String, String> deadLetterConsumer() {
    return new KafkaConsumer<>(
        Map.of(
            ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG,
            String.join(",", kafkaConnectionDetails.getBootstrapServers()),
            ConsumerConfig.GROUP_ID_CONFIG,
            "audit-it-" + UUID.randomUUID(),
            ConsumerConfig.AUTO_OFFSET_RESET_CONFIG,
            "earliest"),
        new StringDeserializer(),
        new StringDeserializer());
  }
}
