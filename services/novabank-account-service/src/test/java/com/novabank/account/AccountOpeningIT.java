package com.novabank.account;

import static com.novabank.account.support.KafkaTestcontainersConfiguration.CUSTOMER_EVENTS_TOPIC;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.novabank.account.application.command.OpenAccountCommand;
import com.novabank.account.application.usecase.OpenAccountUseCase;
import com.novabank.account.domain.model.AccountType;
import com.novabank.account.support.KafkaTestcontainersConfiguration;
import com.novabank.account.support.TestcontainersConfiguration;
import com.zaxxer.hikari.HikariDataSource;
import io.micrometer.registry.otlp.OtlpMeterRegistry;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import javax.sql.DataSource;
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

/** Account opening end to end: KYC event in, Idempotency-Key, ownership, AccountOpened out. */
@SpringBootTest(
    properties = {
      "security.jwt.jwk-set-uri=http://localhost:1/not-used-in-this-test",
      "security.jwt.issuer=http://auth.test",
      "security.jwt.audience=novabank-api",
      "events.account-events.replication-factor=1",
      "events.account-commands.replication-factor=1",
      "events.dead-letter-replication-factor=1",
      "novabank.outbox.relay.poll-interval-ms=200"
    })
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, KafkaTestcontainersConfiguration.class})
class AccountOpeningIT {

  private static final Duration TIMEOUT = Duration.ofSeconds(30);

  @Autowired private MockMvc mockMvc;
  @Autowired private JdbcTemplate jdbcTemplate;
  @Autowired private KafkaTemplate<Object, Object> kafkaTemplate;
  @Autowired private KafkaConnectionDetails kafkaConnectionDetails;
  @Autowired private OpenAccountUseCase openAccountUseCase;

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
  @DisplayName("API docs and Swagger UI are off outside the local profile")
  void apiDocsAreOffByDefault() throws Exception {
    mockMvc.perform(get("/v3/api-docs")).andExpect(status().isNotFound());
    mockMvc.perform(get("/swagger-ui/index.html")).andExpect(status().isNotFound());
  }

  @Test
  @DisplayName(
      "A verified customer opens an account: PENDING, AccountOpened published, ACTIVE once the"
          + " ledger confirms it")
  void verifiedCustomerOpensAccount() throws Exception {
    UUID userId = UUID.randomUUID();
    UUID customerId = verifiedCustomer(userId);

    String body =
        open(userId, "open-key-0001", "CURRENT", "INR")
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.status").value("PENDING"))
            .andExpect(jsonPath("$.currency").value("INR"))
            .andReturn()
            .getResponse()
            .getContentAsString();
    String accountId = JsonPath.read(body, "$.accountId");
    String accountNumber = JsonPath.read(body, "$.accountNumber");

    mockMvc
        .perform(get("/api/v1/accounts").with(customer(userId)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].accountId").value(accountId));

    ConsumerRecord<String, String> event = awaitRecord("account.events.v1", accountId);
    assertThat(header(event, "eventType")).isEqualTo("AccountOpened.v1");
    assertThat((String) JsonPath.read(event.value(), "$.accountNumber")).isEqualTo(accountNumber);
    assertThat((String) JsonPath.read(event.value(), "$.customerId"))
        .isEqualTo(customerId.toString());

    // The ledger answers with LedgerAccountOpened (6.4).
    UUID ledgerEventId = UUID.randomUUID();
    ProducerRecord<Object, Object> ledgerOpened =
        new ProducerRecord<>(
            "ledger.events.v1",
            accountId,
            "{\"eventId\":\"%s\",\"accountId\":\"%s\",\"currency\":\"INR\",\"openedAt\":\"2026-11-20T10:00:00Z\"}"
                .formatted(ledgerEventId, accountId));
    ledgerOpened
        .headers()
        .add("eventType", "LedgerAccountOpened.v1".getBytes(StandardCharsets.UTF_8));
    ledgerOpened.headers().add("eventId", ledgerEventId.toString().getBytes(StandardCharsets.UTF_8));
    kafkaTemplate.send(ledgerOpened).join();

    Awaitility.await()
        .atMost(TIMEOUT)
        .until(() ->
            "ACTIVE"
                .equals(
                    jdbcTemplate.queryForObject(
                        "SELECT status FROM accounts WHERE id = ?::uuid", String.class, accountId)));
    mockMvc
        .perform(get("/api/v1/accounts").with(customer(userId)))
        .andExpect(jsonPath("$[0].status").value("ACTIVE"));
  }

  @Test
  @DisplayName("Same Idempotency-Key: same account, one row; different body with it: 422")
  void idempotencyKey() throws Exception {
    UUID userId = UUID.randomUUID();
    verifiedCustomer(userId);

    String first = accountIdFrom(open(userId, "retry-key-0001", "SAVINGS", "EUR"));
    String retry = accountIdFrom(open(userId, "retry-key-0001", "SAVINGS", "EUR"));

    assertThat(retry).isEqualTo(first);
    assertThat(accountsOf(userId)).isOne();
    open(userId, "retry-key-0001", "SAVINGS", "USD").andExpect(status().isUnprocessableContent());
    // A different key really is a second account.
    open(userId, "retry-key-0002", "SAVINGS", "EUR").andExpect(status().isCreated());
    assertThat(accountsOf(userId)).isEqualTo(2);
  }

  @Test
  @DisplayName("Concurrent requests with the same key open exactly one account")
  void concurrentSameKey() throws Exception {
    UUID userId = UUID.randomUUID();
    verifiedCustomer(userId);
    OpenAccountCommand command =
        new OpenAccountCommand(userId, "race-key-0001", AccountType.CURRENT, "INR");
    Callable<UUID> attempt = () -> openAccountUseCase.open(command).accountId();

    try (ExecutorService pool = Executors.newFixedThreadPool(4)) {
      List<Future<UUID>> results = pool.invokeAll(List.of(attempt, attempt, attempt, attempt));
      assertThat(results.stream().map(AccountOpeningIT::resultOf).distinct()).hasSize(1);
    }
    assertThat(accountsOf(userId)).isOne();
  }

  @Test
  @DisplayName("Without KYC: 403 with a stable problem type; a failed request can be retried")
  void unverifiedCustomer() throws Exception {
    UUID userId = UUID.randomUUID();

    open(userId, "early-key-0001", "CURRENT", "INR")
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.type").value("urn:novabank:problem:customer-not-verified"));

    verifiedCustomer(userId);
    // The failed attempt didn't keep the key: the same key now works.
    open(userId, "early-key-0001", "CURRENT", "INR").andExpect(status().isCreated());
  }

  @Test
  @DisplayName("Validation: missing/invalid Idempotency-Key, unsupported currency, bad type")
  void validation() throws Exception {
    UUID userId = UUID.randomUUID();
    verifiedCustomer(userId);

    mockMvc
        .perform(
            post("/api/v1/accounts")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"type\":\"CURRENT\",\"currency\":\"INR\"}")
                .with(customer(userId)))
        .andExpect(status().isBadRequest());
    open(userId, "short", "CURRENT", "INR").andExpect(status().isBadRequest());
    open(userId, "currency-key-01", "CURRENT", "JPY")
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.detail").value("Accounts in JPY are not offered."));
    open(userId, "type-key-000001", "GOLD", "INR").andExpect(status().isBadRequest());
  }

  @Test
  @DisplayName("Someone else's account is a 404, like a missing one")
  void otherUsersAccount() throws Exception {
    UUID owner = UUID.randomUUID();
    verifiedCustomer(owner);
    String accountId = accountIdFrom(open(owner, "owner-key-0001", "CURRENT", "INR"));

    mockMvc
        .perform(get("/api/v1/accounts/{id}", accountId).with(customer(owner)))
        .andExpect(status().isOk());
    mockMvc
        .perform(get("/api/v1/accounts/{id}", accountId).with(customer(UUID.randomUUID())))
        .andExpect(status().isNotFound());
    mockMvc
        .perform(get("/api/v1/accounts/{id}", UUID.randomUUID()).with(customer(owner)))
        .andExpect(status().isNotFound());
  }

  /** Publishes CustomerVerified like the customer service does and waits until it's applied. */
  private UUID verifiedCustomer(UUID userId) {
    UUID customerId = UUID.randomUUID();
    UUID eventId = UUID.randomUUID();
    ProducerRecord<Object, Object> verified =
        new ProducerRecord<>(
            CUSTOMER_EVENTS_TOPIC,
            customerId.toString(),
            "{\"eventId\":\"%s\",\"customerId\":\"%s\",\"userId\":\"%s\",\"verifiedBy\":\"%s\",\"occurredAt\":\"2026-11-16T10:00:00Z\"}"
                .formatted(eventId, customerId, userId, UUID.randomUUID()));
    verified.headers().add("eventType", "CustomerVerified.v1".getBytes(StandardCharsets.UTF_8));
    verified.headers().add("eventId", eventId.toString().getBytes(StandardCharsets.UTF_8));
    kafkaTemplate.send(verified).join();

    Awaitility.await()
        .atMost(TIMEOUT)
        .until(() ->
            jdbcTemplate.queryForObject(
                    "SELECT count(*) FROM eligible_customers WHERE user_id = ?",
                    Integer.class,
                    userId)
                == 1);
    return customerId;
  }

  private ResultActions open(UUID userId, String key, String type, String currency)
      throws Exception {
    return mockMvc.perform(
        post("/api/v1/accounts")
            .header("Idempotency-Key", key)
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"type\":\"%s\",\"currency\":\"%s\"}".formatted(type, currency))
            .with(customer(userId)));
  }

  private static String accountIdFrom(ResultActions result) throws Exception {
    return JsonPath.read(
        result.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(),
        "$.accountId");
  }

  private Integer accountsOf(UUID userId) {
    return jdbcTemplate.queryForObject(
        "SELECT count(*) FROM accounts WHERE owner_user_id = ?", Integer.class, userId);
  }

  private ConsumerRecord<String, String> awaitRecord(String topic, String key) {
    Map<String, Object> config =
        Map.of(
            ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG,
            String.join(",", kafkaConnectionDetails.getBootstrapServers()),
            ConsumerConfig.GROUP_ID_CONFIG,
            "account-it-" + UUID.randomUUID(),
            ConsumerConfig.AUTO_OFFSET_RESET_CONFIG,
            "earliest",
            ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG,
            StringDeserializer.class,
            ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG,
            StringDeserializer.class);
    try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(config)) {
      consumer.subscribe(List.of(topic));
      long deadline = System.currentTimeMillis() + TIMEOUT.toMillis();
      while (System.currentTimeMillis() < deadline) {
        for (ConsumerRecord<String, String> consumed : consumer.poll(Duration.ofMillis(500))) {
          if (key.equals(consumed.key())) {
            return consumed;
          }
        }
      }
    }
    throw new AssertionError("No record with key " + key + " on " + topic);
  }

  private static String header(ConsumerRecord<String, String> consumed, String name) {
    return new String(consumed.headers().lastHeader(name).value(), StandardCharsets.UTF_8);
  }

  private static UUID resultOf(Future<UUID> result) {
    try {
      return result.get();
    } catch (Exception ex) {
      throw new AssertionError(ex);
    }
  }

  private static RequestPostProcessor customer(UUID userId) {
    return jwt()
        .jwt(token -> token.subject(userId.toString()))
        .authorities(new SimpleGrantedAuthority("ROLE_CUSTOMER"));
  }
}
