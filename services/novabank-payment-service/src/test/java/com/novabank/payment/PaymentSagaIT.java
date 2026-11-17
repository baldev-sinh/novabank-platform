package com.novabank.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.novabank.payment.support.KafkaTestcontainersConfiguration;
import com.novabank.payment.support.TestcontainersConfiguration;
import com.zaxxer.hikari.HikariDataSource;
import io.micrometer.registry.otlp.OtlpMeterRegistry;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import javax.sql.DataSource;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.kafka.autoconfigure.KafkaConnectionDetails;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/**
 * The saga orchestrator against real Kafka and PostgreSQL. This test plays the account and ledger
 * services: it reads the commands the payment service sends and answers in their formats.
 */
@SpringBootTest(
    properties = {
      "security.jwt.jwk-set-uri=http://localhost:1/not-used-in-this-test",
      "security.jwt.issuer=http://auth.test",
      "security.jwt.audience=novabank-api",
      "events.replication-factor=1",
      "novabank.outbox.relay.poll-interval-ms=200",
      "payments.step-timeout=2s",
      "payments.sweeper.interval-ms=500"
    })
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, KafkaTestcontainersConfiguration.class})
class PaymentSagaIT {

  private static final Duration TIMEOUT = Duration.ofSeconds(30);

  @Autowired private MockMvc mockMvc;
  @Autowired private JdbcTemplate jdbcTemplate;
  @Autowired private KafkaTemplate<Object, Object> kafkaTemplate;
  @Autowired private KafkaConnectionDetails kafkaConnectionDetails;

  /** Everything the payment service sent, by topic, collected in the background. */
  private final Map<String, List<ConsumerRecord<String, String>>> sent = new ConcurrentHashMap<>();

  private volatile boolean collecting;
  private Thread collector;

  @BeforeEach
  void startCollecting() {
    collecting = true;
    collector = Thread.ofVirtual().start(this::collect);
  }

  @AfterEach
  void stopCollecting() throws InterruptedException {
    collecting = false;
    collector.join();
  }

  @Autowired private DataSource dataSource;
  @Autowired private ApplicationContext context;

  @Test
  @DisplayName(
      "The connection pool runs with the production settings: fixed size, keepalive, timeouts")
  void connectionPoolSettings() {
    HikariDataSource pool = (HikariDataSource) dataSource;
    assertThat(pool.getMinimumIdle()).isEqualTo(pool.getMaximumPoolSize());
    assertThat(pool.getConnectionTimeout()).isEqualTo(5000);
    assertThat(pool.getValidationTimeout()).isEqualTo(2000);
    assertThat(pool.getMaxLifetime()).isEqualTo(1_800_000);
    assertThat(pool.getKeepaliveTime()).isEqualTo(120_000);
    assertThat(pool.getDataSourceProperties()).containsEntry("tcpKeepAlive", "true");
  }

  @Test
  @DisplayName("Metrics are only scraped by Prometheus: no OTLP push registry")
  void noOtlpMetricsPush() {
    assertThat(context.getBeanNamesForType(OtlpMeterRegistry.class)).isEmpty();
  }

  @Test
  @DisplayName("Transfer: HoldFunds → FundsHeld → PostLedgerTransaction → posted → COMPLETED")
  void transferCompletes() throws Exception {
    UUID user = UUID.randomUUID();
    UUID from = UUID.randomUUID();
    UUID to = UUID.randomUUID();

    String paymentId =
        transfer(user, "pay-key-0001", from, "123456789012", "30.00")
            .andExpect(status().isAccepted())
            .andExpect(jsonPath("$.status").value("INITIATED"))
            .andReturn()
            .getResponse()
            .getContentAsString();
    paymentId = JsonPath.read(paymentId, "$.paymentId");

    ConsumerRecord<String, String> hold =
        awaitCommand("account.commands.v1", paymentId, "HoldFunds.v1", 1).getFirst();
    assertThat(JsonPath.<String>read(hold.value(), "$.requestedBy")).isEqualTo(user.toString());
    assertThat(JsonPath.<String>read(hold.value(), "$.beneficiaryAccountNumber"))
        .isEqualTo("123456789012");

    reply(
        "account.events.v1",
        paymentId,
        "FundsHeld.v1",
        "\"holdId\":\"%s\",\"beneficiaryAccountId\":\"%s\"".formatted(UUID.randomUUID(), to));
    ConsumerRecord<String, String> post =
        awaitCommand("ledger.commands.v1", paymentId, "PostLedgerTransaction.v1", 1).getFirst();
    assertThat(
            JsonPath.<List<String>>read(
                post.value(), "$.postings[?(@.direction == 'DEBIT')].accountId"))
        .containsExactly(from.toString());
    assertThat(
            JsonPath.<List<String>>read(
                post.value(), "$.postings[?(@.direction == 'CREDIT')].accountId"))
        .containsExactly(to.toString());
    assertThat(JsonPath.<String>read(post.value(), "$.type")).isEqualTo("TRANSFER");

    UUID tx = UUID.randomUUID();
    reply(
        "ledger.events.v1",
        paymentId,
        "LedgerTransactionPosted.v1",
        "\"transactionId\":\"%s\"".formatted(tx));
    awaitStatus(paymentId, "LEDGER_POSTED");
    reply(
        "account.events.v1",
        paymentId,
        "BalancesUpdated.v1",
        "\"transactionId\":\"%s\"".formatted(tx));
    awaitStatus(paymentId, "COMPLETED");

    ConsumerRecord<String, String> done =
        awaitCommand("payment.events.v1", paymentId, "PaymentCompleted.v1", 1).getFirst();
    assertThat(JsonPath.<String>read(done.value(), "$.ledgerTransactionId"))
        .isEqualTo(tx.toString());
    mockMvc
        .perform(get("/api/v1/payments/{id}", paymentId).with(customer(user)))
        .andExpect(jsonPath("$.status").value("COMPLETED"))
        .andExpect(jsonPath("$.toAccountId").value(to.toString()));
    mockMvc
        .perform(get("/api/v1/payments/{id}", paymentId).with(customer(UUID.randomUUID())))
        .andExpect(status().isNotFound());
  }

  @Test
  @DisplayName("Same Idempotency-Key: same payment, one saga; different body with it: 422")
  void idempotency() throws Exception {
    UUID user = UUID.randomUUID();
    UUID from = UUID.randomUUID();

    String first = paymentIdOf(transfer(user, "dup-key-00001", from, "123456789012", "5.00"));
    String again = paymentIdOf(transfer(user, "dup-key-00001", from, "123456789012", "5.00"));

    assertThat(again).isEqualTo(first);
    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT count(*) FROM payments WHERE requested_by = ?", Integer.class, user))
        .isOne();
    transfer(user, "dup-key-00001", from, "123456789012", "6.00")
        .andExpect(status().isUnprocessableContent());
  }

  @Test
  @DisplayName("Hold refused: FAILED with the reason; nothing reaches the ledger")
  void holdRefused() throws Exception {
    String paymentId =
        paymentIdOf(
            transfer(
                UUID.randomUUID(), "fail-key-0001", UUID.randomUUID(), "123456789012", "9.00"));
    awaitCommand("account.commands.v1", paymentId, "HoldFunds.v1", 1);

    reply(
        "account.events.v1", paymentId, "FundsHoldFailed.v1", "\"reason\":\"INSUFFICIENT_FUNDS\"");

    awaitStatus(paymentId, "FAILED");
    assertThat(failureReason(paymentId)).isEqualTo("INSUFFICIENT_FUNDS");
    awaitCommand("payment.events.v1", paymentId, "PaymentFailed.v1", 1);
    assertThat(sentFor("ledger.commands.v1", paymentId)).isEmpty();
  }

  @Test
  @DisplayName("Ledger refused after the hold: COMPENSATING → ReleaseHold → HoldReleased → FAILED")
  void compensation() throws Exception {
    String paymentId =
        paymentIdOf(
            transfer(
                UUID.randomUUID(), "comp-key-0001", UUID.randomUUID(), "123456789012", "9.00"));
    awaitCommand("account.commands.v1", paymentId, "HoldFunds.v1", 1);
    reply(
        "account.events.v1",
        paymentId,
        "FundsHeld.v1",
        "\"holdId\":\"%s\",\"beneficiaryAccountId\":\"%s\""
            .formatted(UUID.randomUUID(), UUID.randomUUID()));
    awaitCommand("ledger.commands.v1", paymentId, "PostLedgerTransaction.v1", 1);

    reply(
        "ledger.events.v1",
        paymentId,
        "LedgerPostingFailed.v1",
        "\"reason\":\"INSUFFICIENT_FUNDS\"");
    awaitStatus(paymentId, "COMPENSATING");
    awaitCommand("account.commands.v1", paymentId, "ReleaseHold.v1", 1);

    reply("account.events.v1", paymentId, "HoldReleased.v1", "\"outcome\":\"RELEASED\"");
    awaitStatus(paymentId, "FAILED");
    awaitCommand("payment.events.v1", paymentId, "PaymentFailed.v1", 1);
  }

  @Test
  @DisplayName(
      "BalancesUpdated overtaking LedgerTransactionPosted still completes; the late one is ignored")
  void outOfOrderReplies() throws Exception {
    String paymentId =
        paymentIdOf(
            transfer(
                UUID.randomUUID(), "order-key-001", UUID.randomUUID(), "123456789012", "1.00"));
    awaitCommand("account.commands.v1", paymentId, "HoldFunds.v1", 1);
    reply(
        "account.events.v1",
        paymentId,
        "FundsHeld.v1",
        "\"holdId\":\"%s\",\"beneficiaryAccountId\":\"%s\""
            .formatted(UUID.randomUUID(), UUID.randomUUID()));
    awaitStatus(paymentId, "FUNDS_HELD");

    UUID tx = UUID.randomUUID();
    reply(
        "account.events.v1",
        paymentId,
        "BalancesUpdated.v1",
        "\"transactionId\":\"%s\"".formatted(tx));
    awaitStatus(paymentId, "COMPLETED");
    reply(
        "ledger.events.v1",
        paymentId,
        "LedgerTransactionPosted.v1",
        "\"transactionId\":\"%s\"".formatted(tx));

    Thread.sleep(1000);
    assertThat(statusOf(paymentId)).isEqualTo("COMPLETED");
    assertThat(sentFor("payment.events.v1", paymentId)).hasSize(1);
  }

  @Test
  @DisplayName("No reply: the sweeper re-sends the step (receivers answer the same per payment)")
  void timeoutResendsStep() throws Exception {
    String paymentId =
        paymentIdOf(
            transfer(
                UUID.randomUUID(), "slow-key-0001", UUID.randomUUID(), "123456789012", "2.00"));

    List<ConsumerRecord<String, String>> holds =
        awaitCommand("account.commands.v1", paymentId, "HoldFunds.v1", 2);

    assertThat(header(holds.get(0), "eventId")).isNotEqualTo(header(holds.get(1), "eventId"));
    assertThat(statusOf(paymentId)).isEqualTo("INITIATED");
    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT attempts FROM payments WHERE id = ?::uuid", Integer.class, paymentId))
        .isPositive();
  }

  @Test
  @DisplayName("Deposit (operations): SETTLEMENT → account; customers can't deposit")
  void deposit() throws Exception {
    UUID account = UUID.randomUUID();
    String body =
        mockMvc
            .perform(
                post("/api/v1/admin/deposits")
                    .header("Idempotency-Key", "dep-key-00001")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        "{\"toAccountId\":\"%s\",\"amount\":\"500.00\",\"currency\":\"INR\"}"
                            .formatted(account))
                    .with(staff("OPERATIONS")))
            .andExpect(status().isAccepted())
            .andReturn()
            .getResponse()
            .getContentAsString();
    String paymentId = JsonPath.read(body, "$.paymentId");

    ConsumerRecord<String, String> post =
        awaitCommand("ledger.commands.v1", paymentId, "PostLedgerTransaction.v1", 1).getFirst();
    assertThat(
            JsonPath.<List<String>>read(
                post.value(), "$.postings[?(@.direction == 'DEBIT')].systemAccount"))
        .containsExactly("SETTLEMENT");
    assertThat(sentFor("account.commands.v1", paymentId)).isEmpty();

    UUID tx = UUID.randomUUID();
    reply(
        "ledger.events.v1",
        paymentId,
        "LedgerTransactionPosted.v1",
        "\"transactionId\":\"%s\"".formatted(tx));
    reply(
        "account.events.v1",
        paymentId,
        "BalancesUpdated.v1",
        "\"transactionId\":\"%s\"".formatted(tx));
    awaitStatus(paymentId, "COMPLETED");

    mockMvc
        .perform(
            post("/api/v1/admin/deposits")
                .header("Idempotency-Key", "dep-key-00002")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"toAccountId\":\"%s\",\"amount\":\"5.00\",\"currency\":\"INR\"}"
                        .formatted(account))
                .with(customer(UUID.randomUUID())))
        .andExpect(status().isForbidden());
  }

  @Test
  @DisplayName("Validation: amount format, too many decimals, limit, missing key")
  void validation() throws Exception {
    UUID user = UUID.randomUUID();
    transfer(user, "val-key-00001", UUID.randomUUID(), "123456789012", "-5")
        .andExpect(status().isBadRequest());
    transfer(user, "val-key-00002", UUID.randomUUID(), "123456789012", "1.005")
        .andExpect(status().isBadRequest());
    transfer(user, "val-key-00003", UUID.randomUUID(), "123456789012", "1000000.01")
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.detail").value("Amount is above the limit of 1000000."));
    mockMvc
        .perform(
            post("/api/v1/payments")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(UUID.randomUUID(), "123456789012", "1.00"))
                .with(customer(user)))
        .andExpect(status().isBadRequest());
  }

  // ---------------------------------------------------------------------------------------

  private ResultActions transfer(UUID user, String key, UUID from, String to, String amount)
      throws Exception {
    return mockMvc.perform(
        post("/api/v1/payments")
            .header("Idempotency-Key", key)
            .contentType(MediaType.APPLICATION_JSON)
            .content(body(from, to, amount))
            .with(customer(user)));
  }

  private static String body(UUID from, String to, String amount) {
    return "{\"fromAccountId\":\"%s\",\"toAccountNumber\":\"%s\",\"amount\":\"%s\",\"currency\":\"INR\"}"
        .formatted(from, to, amount);
  }

  private static String paymentIdOf(ResultActions result) throws Exception {
    return JsonPath.read(
        result.andExpect(status().isAccepted()).andReturn().getResponse().getContentAsString(),
        "$.paymentId");
  }

  /** Answers like the account or ledger service: key and paymentId = payment, new event id. */
  private void reply(String topic, String paymentId, String type, String fields) {
    ProducerRecord<Object, Object> record =
        new ProducerRecord<>(
            topic, paymentId, "{\"paymentId\":\"%s\",%s}".formatted(paymentId, fields));
    record.headers().add("eventType", type.getBytes(StandardCharsets.UTF_8));
    record.headers().add("eventId", UUID.randomUUID().toString().getBytes(StandardCharsets.UTF_8));
    kafkaTemplate.send(record).join();
  }

  private void collect() {
    Map<String, Object> config =
        Map.of(
            ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG,
            String.join(",", kafkaConnectionDetails.getBootstrapServers()),
            ConsumerConfig.GROUP_ID_CONFIG,
            "payment-it-" + UUID.randomUUID(),
            ConsumerConfig.AUTO_OFFSET_RESET_CONFIG,
            "earliest",
            ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG,
            StringDeserializer.class,
            ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG,
            StringDeserializer.class);
    try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(config)) {
      consumer.subscribe(List.of("account.commands.v1", "ledger.commands.v1", "payment.events.v1"));
      while (collecting) {
        for (ConsumerRecord<String, String> consumed : consumer.poll(Duration.ofMillis(200))) {
          sent.computeIfAbsent(consumed.topic(), t -> new ArrayList<>()).add(consumed);
        }
      }
    }
  }

  private List<ConsumerRecord<String, String>> sentFor(String topic, String paymentId) {
    return List.copyOf(sent.getOrDefault(topic, List.of())).stream()
        .filter(r -> paymentId.equals(r.key()))
        .toList();
  }

  private List<ConsumerRecord<String, String>> awaitCommand(
      String topic, String paymentId, String type, int count) {
    Awaitility.await()
        .atMost(TIMEOUT)
        .until(() ->
            sentFor(topic, paymentId).stream()
                    .filter(r -> type.equals(header(r, "eventType")))
                    .count()
                >= count);
    return sentFor(topic, paymentId).stream()
        .filter(r -> type.equals(header(r, "eventType")))
        .toList();
  }

  private void awaitStatus(String paymentId, String expected) {
    Awaitility.await().atMost(TIMEOUT).until(() -> expected.equals(statusOf(paymentId)));
  }

  private String statusOf(String paymentId) {
    return jdbcTemplate.queryForObject(
        "SELECT status FROM payments WHERE id = ?::uuid", String.class, paymentId);
  }

  private String failureReason(String paymentId) {
    return jdbcTemplate.queryForObject(
        "SELECT failure_reason FROM payments WHERE id = ?::uuid", String.class, paymentId);
  }

  private static String header(ConsumerRecord<String, String> consumed, String name) {
    return new String(consumed.headers().lastHeader(name).value(), StandardCharsets.UTF_8);
  }

  private static RequestPostProcessor customer(UUID userId) {
    return jwt()
        .jwt(token -> token.subject(userId.toString()))
        .authorities(new SimpleGrantedAuthority("ROLE_CUSTOMER"));
  }

  private static RequestPostProcessor staff(String role) {
    return jwt()
        .jwt(token -> token.subject(UUID.randomUUID().toString()))
        .authorities(new SimpleGrantedAuthority("ROLE_" + role));
  }
}
