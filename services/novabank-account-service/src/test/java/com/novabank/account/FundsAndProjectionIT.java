package com.novabank.account;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.novabank.account.application.command.HoldFundsCommand;
import com.novabank.account.application.command.OpenAccountCommand;
import com.novabank.account.application.port.customer.EligibleCustomerStore;
import com.novabank.account.application.response.AccountResponse;
import com.novabank.account.application.usecase.FundsUseCase;
import com.novabank.account.application.usecase.FundsUseCase.Outcome;
import com.novabank.account.application.usecase.OpenAccountUseCase;
import com.novabank.account.domain.model.AccountType;
import com.novabank.account.support.KafkaTestcontainersConfiguration;
import com.novabank.account.support.TestcontainersConfiguration;
import com.novabank.platform.money.Money;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
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
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;

/** Holds and the balance projection, through real Kafka topics and PostgreSQL. */
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
class FundsAndProjectionIT {

  private static final Duration TIMEOUT = Duration.ofSeconds(30);

  @Autowired private MockMvc mockMvc;
  @Autowired private JdbcTemplate jdbcTemplate;
  @Autowired private TransactionTemplate transactionTemplate;
  @Autowired private KafkaTemplate<Object, Object> kafkaTemplate;
  @Autowired private KafkaConnectionDetails kafkaConnectionDetails;
  @Autowired private OpenAccountUseCase openAccount;
  @Autowired private EligibleCustomerStore eligibleCustomers;
  @Autowired private FundsUseCase funds;

  @Test
  @DisplayName("Saga happy path over Kafka: deposit, hold, ledger posting captures it")
  void holdThenCapture() throws Exception {
    UUID owner = UUID.randomUUID();
    AccountResponse from = account(owner);
    AccountResponse to = account(UUID.randomUUID());
    ledgerPosted(UUID.randomUUID(), entry(from.accountId(), 1, "100.00"));
    awaitBalance(from.accountId(), "100.00");

    UUID payment = UUID.randomUUID();
    sendHold(payment, from.accountId(), owner, to.accountNumber(), "30.00");
    ConsumerRecord<String, String> held = awaitReplies(payment, 1).getFirst();
    assertThat(header(held, "eventType")).isEqualTo("FundsHeld.v1");
    assertThat((String) JsonPath.read(held.value(), "$.beneficiaryAccountId"))
        .isEqualTo(to.accountId().toString());

    mockMvc
        .perform(get("/api/v1/accounts/{id}/balance", from.accountId()).with(customer(owner)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.ledgerBalance").value("100.00"))
        .andExpect(jsonPath("$.held").value("30.00"))
        .andExpect(jsonPath("$.available").value("70.00"));

    // The ledger moves the money: balance and hold change together.
    ledgerPosted(payment, entry(from.accountId(), 2, "70.00"), entry(to.accountId(), 1, "30.00"));
    List<ConsumerRecord<String, String>> replies = awaitReplies(payment, 2);
    assertThat(header(replies.get(1), "eventType")).isEqualTo("BalancesUpdated.v1");
    mockMvc
        .perform(get("/api/v1/accounts/{id}/balance", from.accountId()).with(customer(owner)))
        .andExpect(jsonPath("$.ledgerBalance").value("70.00"))
        .andExpect(jsonPath("$.held").value("0.00"))
        .andExpect(jsonPath("$.available").value("70.00"))
        .andExpect(jsonPath("$.ledgerSequence").value(2));
    assertThat(holdStatus(payment)).isEqualTo("CAPTURED");
    assertThat(ledgerBalance(to.accountId())).isEqualByComparingTo("30.00");
  }

  @Test
  @DisplayName("A stale or repeated ledger event never rolls the balance back")
  void projectionIgnoresStaleEntries() {
    AccountResponse account = account(UUID.randomUUID());
    apply(UUID.randomUUID(), entry(account.accountId(), 1, "50.00"));
    apply(UUID.randomUUID(), entry(account.accountId(), 3, "80.00"));
    // Sequence 2 arrives late; sequence 3 arrives again (ledger re-reply).
    apply(UUID.randomUUID(), entry(account.accountId(), 2, "60.00"));
    apply(UUID.randomUUID(), entry(account.accountId(), 3, "80.00"));

    assertThat(ledgerBalance(account.accountId())).isEqualByComparingTo("80.00");
  }

  @Test
  @DisplayName("10 concurrent holds of 30 on 100: exactly 3 succeed")
  void concurrentHoldsNeverOverspend() throws Exception {
    UUID owner = UUID.randomUUID();
    AccountResponse from = account(owner);
    AccountResponse to = account(UUID.randomUUID());
    apply(UUID.randomUUID(), entry(from.accountId(), 1, "100.00"));

    List<Callable<Outcome>> attempts = new ArrayList<>();
    for (int i = 0; i < 10; i++) {
      attempts.add(() ->
          funds.hold(hold(UUID.randomUUID(), from.accountId(), owner, to.accountNumber(), "30")));
    }
    List<Outcome> outcomes = new ArrayList<>();
    try (ExecutorService pool = Executors.newFixedThreadPool(10)) {
      for (Future<Outcome> result : pool.invokeAll(attempts)) {
        outcomes.add(result.get());
      }
    }

    assertThat(outcomes).filteredOn(o -> o == Outcome.APPLIED).hasSize(3);
    assertThat(outcomes).filteredOn(o -> o == Outcome.REJECTED).hasSize(7);
    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT sum(amount) FROM account_holds WHERE account_id = ? AND status = 'ACTIVE'",
                BigDecimal.class,
                from.accountId()))
        .isEqualByComparingTo("90.00");
  }

  @Test
  @DisplayName("Refusals are final per payment; validation covers owner, beneficiary and currency")
  void refusals() {
    UUID owner = UUID.randomUUID();
    AccountResponse from = account(owner);
    AccountResponse to = account(UUID.randomUUID());
    apply(UUID.randomUUID(), entry(from.accountId(), 1, "20.00"));

    UUID tooMuch = UUID.randomUUID();
    assertThat(funds.hold(hold(tooMuch, from.accountId(), owner, to.accountNumber(), "50")))
        .isEqualTo(Outcome.REJECTED);
    apply(UUID.randomUUID(), entry(from.accountId(), 2, "500.00"));
    // Money has arrived, but the same payment stays refused.
    assertThat(funds.hold(hold(tooMuch, from.accountId(), owner, to.accountNumber(), "50")))
        .isEqualTo(Outcome.REJECTED);
    assertThat(holdStatus(tooMuch)).isNull();

    assertThat(
            funds.hold(
                hold(
                    UUID.randomUUID(),
                    from.accountId(),
                    UUID.randomUUID(),
                    to.accountNumber(),
                    "1")))
        .isEqualTo(Outcome.REJECTED);
    assertThat(funds.hold(hold(UUID.randomUUID(), from.accountId(), owner, "000000000000", "1")))
        .isEqualTo(Outcome.REJECTED);
    assertThat(
            funds.hold(hold(UUID.randomUUID(), from.accountId(), owner, from.accountNumber(), "1")))
        .isEqualTo(Outcome.REJECTED);
    assertThat(
            jdbcTemplate.queryForList(
                "SELECT DISTINCT reason FROM hold_rejections ORDER BY 1", String.class))
        .contains(
            "INSUFFICIENT_FUNDS", "NOT_ACCOUNT_OWNER", "BENEFICIARY_NOT_FOUND", "SAME_ACCOUNT");
  }

  @Test
  @DisplayName(
      "Compensation: release frees the money; releasing twice or without a hold still answers")
  void release() throws Exception {
    UUID owner = UUID.randomUUID();
    AccountResponse from = account(owner);
    AccountResponse to = account(UUID.randomUUID());
    apply(UUID.randomUUID(), entry(from.accountId(), 1, "40.00"));
    UUID payment = UUID.randomUUID();
    assertThat(funds.hold(hold(payment, from.accountId(), owner, to.accountNumber(), "40")))
        .isEqualTo(Outcome.APPLIED);

    sendRelease(payment);
    sendRelease(payment);
    UUID never = UUID.randomUUID();
    sendRelease(never);

    List<ConsumerRecord<String, String>> replies = awaitReplies(payment, 3);
    assertThat(JsonPath.<String>read(replies.get(1).value(), "$.outcome")).isEqualTo("RELEASED");
    assertThat(JsonPath.<String>read(replies.get(2).value(), "$.outcome")).isEqualTo("RELEASED");
    assertThat(JsonPath.<String>read(awaitReplies(never, 1).getFirst().value(), "$.outcome"))
        .isEqualTo("NO_HOLD");
    assertThat(holdStatus(payment)).isEqualTo("RELEASED");
    mockMvc
        .perform(get("/api/v1/accounts/{id}/balance", from.accountId()).with(customer(owner)))
        .andExpect(jsonPath("$.available").value("40.00"));
    mockMvc
        .perform(
            get("/api/v1/accounts/{id}/balance", from.accountId())
                .with(customer(UUID.randomUUID())))
        .andExpect(status().isNotFound());
  }

  // ---------------------------------------------------------------------------------------

  /** Opens an account and activates it the way the ledger does (LedgerAccountOpened, 6.4). */
  private AccountResponse account(UUID owner) {
    transactionTemplate.executeWithoutResult(
        tx -> eligibleCustomers.save(UUID.randomUUID(), owner, Instant.now()));
    AccountResponse opened =
        openAccount.open(
            new OpenAccountCommand(owner, "key-" + UUID.randomUUID(), AccountType.CURRENT, "INR"));
    UUID eventId = UUID.randomUUID();
    sendWithId(
        "ledger.events.v1",
        opened.accountId(),
        "LedgerAccountOpened.v1",
        eventId,
        "{\"accountId\":\"%s\",\"currency\":\"INR\"}".formatted(opened.accountId()));
    Awaitility.await()
        .atMost(TIMEOUT)
        .until(() ->
            "ACTIVE"
                .equals(
                    jdbcTemplate.queryForObject(
                        "SELECT status FROM accounts WHERE id = ?",
                        String.class,
                        opened.accountId())));
    return opened;
  }

  @Test
  @DisplayName("A PENDING account (ledger hasn't confirmed it yet) can't send or receive money")
  void pendingAccountIsRefusedUpFront() {
    UUID owner = UUID.randomUUID();
    transactionTemplate.executeWithoutResult(
        tx -> eligibleCustomers.save(UUID.randomUUID(), owner, Instant.now()));
    AccountResponse pending =
        openAccount.open(
            new OpenAccountCommand(owner, "key-" + UUID.randomUUID(), AccountType.CURRENT, "INR"));
    AccountResponse active = account(owner);

    UUID fromPending = UUID.randomUUID();
    UUID toPending = UUID.randomUUID();
    assertThat(funds.hold(hold(fromPending, pending.accountId(), owner, active.accountNumber(), "1")))
        .isEqualTo(Outcome.REJECTED);
    assertThat(funds.hold(hold(toPending, active.accountId(), owner, pending.accountNumber(), "1")))
        .isEqualTo(Outcome.REJECTED);

    assertThat(rejectionReason(fromPending)).isEqualTo("ACCOUNT_NOT_ACTIVE");
    assertThat(rejectionReason(toPending)).isEqualTo("BENEFICIARY_NOT_ACTIVE");
  }

  private String rejectionReason(UUID payment) {
    return jdbcTemplate.queryForObject(
        "SELECT reason FROM hold_rejections WHERE payment_id = ?", String.class, payment);
  }

  private static HoldFundsCommand hold(
      UUID payment, UUID account, UUID requestedBy, String beneficiary, String amount) {
    return new HoldFundsCommand(
        UUID.randomUUID(), payment, account, requestedBy, beneficiary, Money.of(amount, "INR"));
  }

  private static String entry(UUID account, long sequence, String balanceAfter) {
    return ("{\"accountId\":\"%s\",\"direction\":\"CREDIT\",\"amount\":\"1.00\","
            + "\"currency\":\"INR\",\"balanceAfter\":\"%s\",\"sequence\":%d}")
        .formatted(account, balanceAfter, sequence);
  }

  /** Publishes LedgerTransactionPosted like the ledger service does. */
  private void ledgerPosted(UUID payment, String... entries) {
    send(
        "ledger.events.v1",
        payment,
        "LedgerTransactionPosted.v1",
        "{\"paymentId\":\"%s\",\"transactionId\":\"%s\",\"type\":\"TRANSFER\",\"entries\":[%s]}"
            .formatted(payment, UUID.randomUUID(), String.join(",", entries)));
  }

  /** Same as {@link #ledgerPosted}, and waits until the projection has handled it. */
  private void apply(UUID payment, String... entries) {
    UUID eventId = UUID.randomUUID();
    sendWithId(
        "ledger.events.v1",
        payment,
        "LedgerTransactionPosted.v1",
        eventId,
        "{\"paymentId\":\"%s\",\"transactionId\":\"%s\",\"type\":\"TRANSFER\",\"entries\":[%s]}"
            .formatted(payment, UUID.randomUUID(), String.join(",", entries)));
    Awaitility.await()
        .atMost(TIMEOUT)
        .until(() ->
            jdbcTemplate.queryForObject(
                    "SELECT count(*) FROM processed_events WHERE event_id = ?",
                    Integer.class,
                    eventId)
                == 1);
  }

  private void sendHold(UUID payment, UUID account, UUID owner, String beneficiary, String amount) {
    send(
        "account.commands.v1",
        payment,
        "HoldFunds.v1",
        ("{\"paymentId\":\"%s\",\"accountId\":\"%s\",\"requestedBy\":\"%s\","
                + "\"beneficiaryAccountNumber\":\"%s\",\"amount\":\"%s\",\"currency\":\"INR\"}")
            .formatted(payment, account, owner, beneficiary, amount));
  }

  private void sendRelease(UUID payment) {
    send(
        "account.commands.v1",
        payment,
        "ReleaseHold.v1",
        "{\"paymentId\":\"%s\"}".formatted(payment));
  }

  private void send(String topic, UUID key, String type, String json) {
    sendWithId(topic, key, type, UUID.randomUUID(), json);
  }

  private void sendWithId(String topic, UUID key, String type, UUID id, String json) {
    ProducerRecord<Object, Object> record = new ProducerRecord<>(topic, key.toString(), json);
    record.headers().add("eventType", type.getBytes(StandardCharsets.UTF_8));
    record.headers().add("eventId", id.toString().getBytes(StandardCharsets.UTF_8));
    kafkaTemplate.send(record).join();
  }

  /** Saga replies on account.events.v1 for this payment, in order. */
  private List<ConsumerRecord<String, String>> awaitReplies(UUID payment, int count) {
    Map<String, Object> config =
        Map.of(
            ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG,
            String.join(",", kafkaConnectionDetails.getBootstrapServers()),
            ConsumerConfig.GROUP_ID_CONFIG,
            "funds-it-" + UUID.randomUUID(),
            ConsumerConfig.AUTO_OFFSET_RESET_CONFIG,
            "earliest",
            ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG,
            StringDeserializer.class,
            ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG,
            StringDeserializer.class);
    List<ConsumerRecord<String, String>> replies = new ArrayList<>();
    try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(config)) {
      consumer.subscribe(List.of("account.events.v1"));
      long deadline = System.currentTimeMillis() + TIMEOUT.toMillis();
      while (replies.size() < count && System.currentTimeMillis() < deadline) {
        for (ConsumerRecord<String, String> consumed : consumer.poll(Duration.ofMillis(500))) {
          if (payment.toString().equals(consumed.key())) {
            replies.add(consumed);
          }
        }
      }
    }
    assertThat(replies).as("replies for payment " + payment).hasSizeGreaterThanOrEqualTo(count);
    return replies;
  }

  private void awaitBalance(UUID account, String expected) {
    Awaitility.await()
        .atMost(TIMEOUT)
        .until(() ->
            ledgerBalance(account) != null
                && ledgerBalance(account).compareTo(new BigDecimal(expected)) == 0);
  }

  private BigDecimal ledgerBalance(UUID account) {
    return jdbcTemplate
        .queryForList(
            "SELECT ledger_balance FROM account_balances WHERE account_id = ?",
            BigDecimal.class,
            account)
        .stream()
        .findFirst()
        .orElse(null);
  }

  private String holdStatus(UUID payment) {
    return jdbcTemplate
        .queryForList(
            "SELECT status FROM account_holds WHERE payment_id = ?", String.class, payment)
        .stream()
        .findFirst()
        .orElse(null);
  }

  private static String header(ConsumerRecord<String, String> consumed, String name) {
    return new String(consumed.headers().lastHeader(name).value(), StandardCharsets.UTF_8);
  }

  private static org.springframework.test.web.servlet.request.RequestPostProcessor customer(
      UUID userId) {
    return jwt()
        .jwt(token -> token.subject(userId.toString()))
        .authorities(new SimpleGrantedAuthority("ROLE_CUSTOMER"));
  }
}
