package com.novabank.ledger;

import static com.novabank.ledger.support.KafkaTestcontainersConfiguration.ACCOUNT_EVENTS_TOPIC;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.novabank.ledger.application.command.PostLedgerTransactionCommand;
import com.novabank.ledger.application.command.PostLedgerTransactionCommand.Instruction;
import com.novabank.ledger.application.usecase.PostLedgerTransactionUseCase;
import com.novabank.ledger.application.usecase.PostLedgerTransactionUseCase.Outcome;
import com.novabank.ledger.domain.model.Direction;
import com.novabank.ledger.domain.model.TransactionType;
import com.novabank.ledger.support.KafkaTestcontainersConfiguration;
import com.novabank.ledger.support.TestcontainersConfiguration;
import com.novabank.platform.money.Money;
import com.zaxxer.hikari.HikariDataSource;
import io.micrometer.registry.otlp.OtlpMeterRegistry;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;

/** The ledger against a real PostgreSQL and Kafka: double entry, finality, immutability. */
@SpringBootTest(
    properties = {
      "security.jwt.jwk-set-uri=http://localhost:1/not-used-in-this-test",
      "security.jwt.issuer=http://auth.test",
      "security.jwt.audience=novabank-api",
      "events.replication-factor=1",
      "novabank.outbox.relay.poll-interval-ms=200"
    })
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, KafkaTestcontainersConfiguration.class})
class LedgerPostingIT {

  private static final Duration TIMEOUT = Duration.ofSeconds(30);

  @Autowired private MockMvc mockMvc;
  @Autowired private JdbcTemplate jdbcTemplate;
  @Autowired private TransactionTemplate transactionTemplate;
  @Autowired private KafkaTemplate<Object, Object> kafkaTemplate;
  @Autowired private KafkaConnectionDetails kafkaConnectionDetails;
  @Autowired private PostLedgerTransactionUseCase postLedgerTransaction;

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
  @DisplayName("Kafka round trip: AccountOpened, deposit command, LedgerTransactionPosted reply")
  void depositThroughKafka() throws Exception {
    UUID account = openAccount("INR");
    UUID paymentId = UUID.randomUUID();

    sendCommand(
        paymentId,
        "DEPOSIT",
        settlement("DEBIT", "100.00", "INR"),
        customer(account, "CREDIT", "100.00", "INR"));

    ConsumerRecord<String, String> reply = awaitReplies(paymentId, 1).getFirst();
    assertThat(header(reply, "eventType")).isEqualTo("LedgerTransactionPosted.v1");
    String json = reply.value();
    String entry = "$.entries[?(@.accountId == '" + account + "')]";
    assertThat(JsonPath.<List<String>>read(json, entry + ".balanceAfter"))
        .containsExactly("100.00");
    assertThat(JsonPath.<List<Integer>>read(json, entry + ".sequence")).containsExactly(1);

    mockMvc
        .perform(get("/api/v1/ledger/accounts/{id}", account).with(staff("OPERATIONS")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.balance").value("100.00"))
        .andExpect(jsonPath("$.kind").value("CUSTOMER"));
  }

  @Test
  @DisplayName("The outcome per payment is final: a re-sent command gets the same answer")
  void outcomeIsFinal() throws Exception {
    UUID from = openAccount("INR");
    UUID to = openAccount("INR");
    deposit(from, "50.00");

    UUID tooMuch = UUID.randomUUID();
    sendCommand(
        tooMuch,
        "TRANSFER",
        customer(from, "DEBIT", "80.00", "INR"),
        customer(to, "CREDIT", "80.00", "INR"));
    ConsumerRecord<String, String> refused = awaitReplies(tooMuch, 1).getFirst();
    assertThat(header(refused, "eventType")).isEqualTo("LedgerPostingFailed.v1");
    assertThat((String) JsonPath.read(refused.value(), "$.reason")).isEqualTo("INSUFFICIENT_FUNDS");

    // Money arrives, then the saga re-sends the same payment: still refused, nothing posted.
    deposit(from, "100.00");
    sendCommand(
        tooMuch,
        "TRANSFER",
        customer(from, "DEBIT", "80.00", "INR"),
        customer(to, "CREDIT", "80.00", "INR"));
    List<ConsumerRecord<String, String>> replies = awaitReplies(tooMuch, 2);
    assertThat(header(replies.get(1), "eventType")).isEqualTo("LedgerPostingFailed.v1");
    assertThat(transactionsFor(tooMuch)).isZero();

    // A posted payment re-sent: the same transaction is reported again, not a second one.
    UUID ok = UUID.randomUUID();
    sendCommand(
        ok,
        "TRANSFER",
        customer(from, "DEBIT", "30.00", "INR"),
        customer(to, "CREDIT", "30.00", "INR"));
    sendCommand(
        ok,
        "TRANSFER",
        customer(from, "DEBIT", "30.00", "INR"),
        customer(to, "CREDIT", "30.00", "INR"));
    List<ConsumerRecord<String, String>> posted = awaitReplies(ok, 2);
    assertThat((String) JsonPath.read(posted.get(0).value(), "$.transactionId"))
        .isEqualTo(JsonPath.read(posted.get(1).value(), "$.transactionId"));
    assertThat(transactionsFor(ok)).isOne();
    assertThat(balanceOf(from)).isEqualByComparingTo("120.00");
    assertThat(balanceOf(to)).isEqualByComparingTo("30.00");
  }

  @Test
  @DisplayName("Redelivery of the same message is a no-op")
  void duplicateMessage() {
    UUID account = openAccount("INR");
    PostLedgerTransactionCommand command =
        new PostLedgerTransactionCommand(
            UUID.randomUUID(),
            UUID.randomUUID(),
            TransactionType.DEPOSIT,
            List.of(systemInstruction("DEBIT", "5"), instruction(account, "CREDIT", "5")));

    assertThat(postLedgerTransaction.post(command)).isEqualTo(Outcome.POSTED);
    assertThat(postLedgerTransaction.post(command)).isEqualTo(Outcome.DUPLICATE);
    assertThat(balanceOf(account)).isEqualByComparingTo("5.00");
  }

  @Test
  @DisplayName("Unknown accounts, currency mismatches and unbalanced postings are refused")
  void businessRejections() {
    UUID inr = openAccount("INR");

    assertThat(
            post(
                TransactionType.DEPOSIT,
                systemInstruction("DEBIT", "5"),
                instruction(UUID.randomUUID(), "CREDIT", "5")))
        .isEqualTo(Outcome.REJECTED);
    assertThat(
            post(
                TransactionType.DEPOSIT,
                new Instruction(null, "SETTLEMENT", Direction.DEBIT, Money.of("5", "EUR")),
                new Instruction(inr, null, Direction.CREDIT, Money.of("5", "EUR"))))
        .isEqualTo(Outcome.REJECTED);
    assertThat(
            post(
                TransactionType.DEPOSIT,
                systemInstruction("DEBIT", "5"),
                instruction(inr, "CREDIT", "4")))
        .isEqualTo(Outcome.REJECTED);
    assertThat(
            jdbcTemplate.queryForList(
                "SELECT DISTINCT reason FROM rejected_postings ORDER BY reason", String.class))
        .contains("CURRENCY_MISMATCH", "INVALID_POSTINGS", "UNKNOWN_ACCOUNT");
    assertThat(balanceOf(inr)).isEqualByComparingTo("0");
  }

  @Test
  @DisplayName("10 concurrent withdrawals of 30 from 100: exactly 3 succeed, never negative")
  void concurrentWithdrawalsNeverOverdraw() throws Exception {
    UUID from = openAccount("INR");
    UUID to = openAccount("INR");
    deposit(from, "100.00");

    Callable<Outcome> withdraw =
        () ->
            post(
                TransactionType.TRANSFER,
                instruction(from, "DEBIT", "30"),
                instruction(to, "CREDIT", "30"));
    List<Callable<Outcome>> attempts = new ArrayList<>();
    for (int i = 0; i < 10; i++) {
      attempts.add(withdraw);
    }
    List<Outcome> outcomes = new ArrayList<>();
    try (ExecutorService pool = Executors.newFixedThreadPool(10)) {
      for (Future<Outcome> result : pool.invokeAll(attempts)) {
        outcomes.add(result.get());
      }
    }

    assertThat(outcomes).filteredOn(o -> o == Outcome.POSTED).hasSize(3);
    assertThat(outcomes).filteredOn(o -> o == Outcome.REJECTED).hasSize(7);
    assertThat(balanceOf(from)).isEqualByComparingTo("10.00");
    assertThat(balanceOf(to)).isEqualByComparingTo("90.00");
    // Sequences are gapless: 1 deposit + 3 withdrawals.
    assertThat(
            jdbcTemplate.queryForList(
                "SELECT account_sequence FROM ledger_entries WHERE account_id = ? ORDER BY 1",
                Long.class,
                from))
        .containsExactly(1L, 2L, 3L, 4L);
  }

  @Test
  @DisplayName("The database itself rejects changes to ledger rows and unbalanced commits")
  void databaseEnforcesInvariants() {
    UUID account = openAccount("INR");
    deposit(account, "10.00");

    assertThatThrownBy(() -> jdbcTemplate.update("UPDATE ledger_entries SET amount = 1"))
        .hasMessageContaining("immutable");
    assertThatThrownBy(() -> jdbcTemplate.update("DELETE FROM ledger_transactions"))
        .hasMessageContaining("immutable");
    assertThatThrownBy(() -> jdbcTemplate.execute("TRUNCATE ledger_entries CASCADE"))
        .hasMessageContaining("immutable");

    UUID transactionId = UUID.randomUUID();
    assertThatThrownBy(() ->
            transactionTemplate.executeWithoutResult(status -> {
              jdbcTemplate.update(
                  "INSERT INTO ledger_transactions VALUES (?, ?, 'DEPOSIT', now())",
                  transactionId,
                  UUID.randomUUID());
              jdbcTemplate.update(
                  "INSERT INTO ledger_entries VALUES (?, ?, ?, 'CREDIT', 5, 'INR', 15, 99)",
                  UUID.randomUUID(),
                  transactionId,
                  account);
            }))
        .rootCause()
        .hasMessageContaining("not balanced");
    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT count(*) FROM ledger_transactions WHERE id = ?",
                Integer.class,
                transactionId))
        .isZero();
  }

  @Test
  @DisplayName("Reconciliation is clean; the API is staff-only and read-only")
  void reconciliationAndAccess() throws Exception {
    UUID account = openAccount("INR");
    deposit(account, "7.00");

    mockMvc
        .perform(get("/api/v1/ledger/reconciliation").with(staff("ADMIN")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.balanced").value(true));
    mockMvc
        .perform(get("/api/v1/ledger/accounts/{id}/entries", account).with(staff("OPERATIONS")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].direction").value("CREDIT"))
        .andExpect(jsonPath("$[0].amount").value("7.00"));
    mockMvc
        .perform(get("/api/v1/ledger/accounts/{id}", account).with(staff("CUSTOMER")))
        .andExpect(status().isForbidden());
    mockMvc
        .perform(get("/api/v1/ledger/accounts/{id}", UUID.randomUUID()).with(staff("OPERATIONS")))
        .andExpect(status().isNotFound());
  }

  // ---------------------------------------------------------------------------------------

  /** Publishes AccountOpened like the account service and waits for the ledger account. */
  private UUID openAccount(String currency) {
    UUID accountId = UUID.randomUUID();
    UUID eventId = UUID.randomUUID();
    ProducerRecord<Object, Object> opened =
        new ProducerRecord<>(
            ACCOUNT_EVENTS_TOPIC,
            accountId.toString(),
            "{\"eventId\":\"%s\",\"accountId\":\"%s\",\"currency\":\"%s\",\"type\":\"CURRENT\",\"occurredAt\":\"2026-11-16T10:00:00Z\"}"
                .formatted(eventId, accountId, currency));
    opened.headers().add("eventType", "AccountOpened.v1".getBytes(StandardCharsets.UTF_8));
    opened.headers().add("eventId", eventId.toString().getBytes(StandardCharsets.UTF_8));
    kafkaTemplate.send(opened).join();
    Awaitility.await()
        .atMost(TIMEOUT)
        .until(() ->
            jdbcTemplate.queryForObject(
                    "SELECT count(*) FROM ledger_accounts WHERE id = ?", Integer.class, accountId)
                == 1);
    return accountId;
  }

  private void deposit(UUID account, String amount) {
    assertThat(
            post(
                TransactionType.DEPOSIT,
                systemInstruction("DEBIT", amount),
                instruction(account, "CREDIT", amount)))
        .isEqualTo(Outcome.POSTED);
  }

  private Outcome post(TransactionType type, Instruction... postings) {
    return postLedgerTransaction.post(
        new PostLedgerTransactionCommand(
            UUID.randomUUID(), UUID.randomUUID(), type, List.of(postings)));
  }

  private static Instruction instruction(UUID account, String direction, String amount) {
    return new Instruction(account, null, Direction.valueOf(direction), Money.of(amount, "INR"));
  }

  private static Instruction systemInstruction(String direction, String amount) {
    return new Instruction(
        null, "SETTLEMENT", Direction.valueOf(direction), Money.of(amount, "INR"));
  }

  private static String customer(UUID account, String direction, String amount, String currency) {
    return "{\"accountId\":\"%s\",\"direction\":\"%s\",\"amount\":\"%s\",\"currency\":\"%s\"}"
        .formatted(account, direction, amount, currency);
  }

  private static String settlement(String direction, String amount, String currency) {
    return "{\"systemAccount\":\"SETTLEMENT\",\"direction\":\"%s\",\"amount\":\"%s\",\"currency\":\"%s\"}"
        .formatted(direction, amount, currency);
  }

  /** Sends PostLedgerTransaction like the payment service does (new message id each time). */
  private void sendCommand(UUID paymentId, String type, String... postings) {
    UUID commandId = UUID.randomUUID();
    ProducerRecord<Object, Object> command =
        new ProducerRecord<>(
            "ledger.commands.v1",
            paymentId.toString(),
            "{\"paymentId\":\"%s\",\"type\":\"%s\",\"postings\":[%s]}"
                .formatted(paymentId, type, String.join(",", postings)));
    command.headers().add("eventType", "PostLedgerTransaction.v1".getBytes(StandardCharsets.UTF_8));
    command.headers().add("eventId", commandId.toString().getBytes(StandardCharsets.UTF_8));
    kafkaTemplate.send(command).join();
  }

  /** Reads ledger.events.v1 until this payment has at least {@code count} replies. */
  private List<ConsumerRecord<String, String>> awaitReplies(UUID paymentId, int count) {
    Map<String, Object> config =
        Map.of(
            ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG,
            String.join(",", kafkaConnectionDetails.getBootstrapServers()),
            ConsumerConfig.GROUP_ID_CONFIG,
            "ledger-it-" + UUID.randomUUID(),
            ConsumerConfig.AUTO_OFFSET_RESET_CONFIG,
            "earliest",
            ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG,
            StringDeserializer.class,
            ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG,
            StringDeserializer.class);
    List<ConsumerRecord<String, String>> replies = new ArrayList<>();
    try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(config)) {
      consumer.subscribe(List.of("ledger.events.v1"));
      long deadline = System.currentTimeMillis() + TIMEOUT.toMillis();
      while (replies.size() < count && System.currentTimeMillis() < deadline) {
        for (ConsumerRecord<String, String> consumed : consumer.poll(Duration.ofMillis(500))) {
          if (paymentId.toString().equals(consumed.key())) {
            replies.add(consumed);
          }
        }
      }
    }
    assertThat(replies).as("replies for payment " + paymentId).hasSizeGreaterThanOrEqualTo(count);
    return replies;
  }

  private BigDecimal balanceOf(UUID account) {
    return jdbcTemplate.queryForObject(
        "SELECT balance FROM ledger_accounts WHERE id = ?", BigDecimal.class, account);
  }

  private Integer transactionsFor(UUID paymentId) {
    return jdbcTemplate.queryForObject(
        "SELECT count(*) FROM ledger_transactions WHERE payment_id = ?", Integer.class, paymentId);
  }

  private static String header(ConsumerRecord<String, String> consumed, String name) {
    return new String(consumed.headers().lastHeader(name).value(), StandardCharsets.UTF_8);
  }

  private static org.springframework.test.web.servlet.request.RequestPostProcessor staff(
      String role) {
    return jwt()
        .jwt(token -> token.subject(UUID.randomUUID().toString()))
        .authorities(new SimpleGrantedAuthority("ROLE_" + role));
  }
}
