package com.novabank.notification;

import static org.assertj.core.api.Assertions.assertThat;

import com.novabank.notification.support.NotificationTestcontainers;
import com.novabank.notification.support.NotificationTestcontainers.Mailbox;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
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
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;

/**
 * Events in, emails out, against real PostgreSQL, Kafka and an SMTP server (Mailpit): the right
 * people get the right email once, out-of-order events still end in an email, old events send none,
 * unreadable ones are dead-lettered.
 */
@SpringBootTest(
    properties = {
      // Mail auto-configuration's condition is read before Mailpit's address is registered;
      // NotificationTestcontainers then points it at the container.
      "spring.mail.host=localhost",
      "notifications.dead-letter-replication-factor=1",
      "notifications.dispatch-interval=200ms",
      "notifications.retry-initial=500ms",
      "notifications.retry-max=1s"
    })
@Import(NotificationTestcontainers.class)
class NotificationFlowIT {

  private static final Duration TIMEOUT = Duration.ofSeconds(30);

  @Autowired private KafkaTemplate<Object, Object> kafkaTemplate;
  @Autowired private KafkaConnectionDetails kafkaConnectionDetails;
  @Autowired private JdbcTemplate jdbcTemplate;
  @Autowired private Mailbox mailbox;

  @Test
  @DisplayName("Account ready, transfer sent and money received go to the right people, once")
  void transferFlow() {
    Party sender = party("sender");
    Party beneficiary = party("beneficiary");
    send(
        "account.events.v1",
        sender.account,
        "AccountActivated.v1",
        UUID.randomUUID(),
        account(sender, "100000001111"));
    UUID paymentEvent = UUID.randomUUID();
    String completed = payment("TRANSFER", sender, beneficiary, "120.00", null);
    send("payment.events.v1", UUID.randomUUID(), "PaymentCompleted.v1", paymentEvent, completed);
    // Redelivered: no second email.
    send("payment.events.v1", UUID.randomUUID(), "PaymentCompleted.v1", paymentEvent, completed);

    await(sender.email, 2);
    await(beneficiary.email, 1);
    assertThat(mailbox.subjectsTo(sender.email))
        .containsExactlyInAnyOrder("Your NovaBank account is ready", "Transfer sent");
    assertThat(String.join("\n", mailbox.textsTo(sender.email)))
        .contains("You sent 120.00 INR to account •••• " + last4(beneficiary))
        .contains("Your new INR account •••• 1111 is open");
    assertThat(mailbox.textsTo(beneficiary.email).getFirst())
        .contains("120.00 INR arrived in account •••• " + last4(beneficiary))
        .doesNotContain(beneficiary.number);
    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT count(*) FROM notifications WHERE event_id = ?",
                Integer.class,
                paymentEvent))
        .isEqualTo(2);
  }

  @Test
  @DisplayName("A deposit seen before its account and owner is emailed once they arrive")
  void outOfOrder() {
    Party owner =
        new Party(
            UUID.randomUUID(),
            UUID.randomUUID(),
            "late-" + UUID.randomUUID() + "@example.com",
            "200000002222");
    UUID event = UUID.randomUUID();
    send(
        "payment.events.v1",
        UUID.randomUUID(),
        "PaymentCompleted.v1",
        event,
        payment("DEPOSIT", null, owner, "500.00", null));

    Awaitility.await()
        .atMost(TIMEOUT)
        .until(() -> lastError(event) != null && lastError(event).contains("not known yet"));
    register(owner);

    await(owner.email, 1);
    assertThat(mailbox.subjectsTo(owner.email)).containsExactly("Money received");
    assertThat(statusOf(event)).isEqualTo("SENT");
  }

  @Test
  @DisplayName("A failed transfer tells the sender why; a failed deposit sends nothing")
  void failures() {
    Party sender = party("failed");
    Party beneficiary = party("nobody");
    send(
        "payment.events.v1",
        UUID.randomUUID(),
        "PaymentFailed.v1",
        UUID.randomUUID(),
        payment("TRANSFER", sender, beneficiary, "1000.00", "INSUFFICIENT_FUNDS"));
    UUID failedDeposit = UUID.randomUUID();
    send(
        "payment.events.v1",
        UUID.randomUUID(),
        "PaymentFailed.v1",
        failedDeposit,
        payment("DEPOSIT", null, beneficiary, "5.00", "UNKNOWN_ACCOUNT"));

    await(sender.email, 1);
    assertThat(mailbox.textsTo(sender.email).getFirst())
        .contains("1000.00 INR was not completed (insufficient funds)");
    Awaitility.await().atMost(TIMEOUT).until(() -> processed(failedDeposit));
    assertThat(mailbox.subjectsTo(beneficiary.email)).isEmpty();
  }

  @Test
  @DisplayName("Events older than max-event-age update the projection but send no email")
  void oldEventsSendNothing() {
    Party owner = party("history");
    UUID event = UUID.randomUUID();
    long twoDaysAgo = Instant.now().minus(Duration.ofDays(2)).toEpochMilli();
    ProducerRecord<Object, Object> old =
        new ProducerRecord<>(
            "payment.events.v1",
            null,
            twoDaysAgo,
            UUID.randomUUID().toString(),
            payment("DEPOSIT", null, owner, "9.00", null));
    headers(old, "PaymentCompleted.v1", event);
    kafkaTemplate.send(old).join();

    Awaitility.await().atMost(TIMEOUT).until(() -> processed(event));
    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT count(*) FROM notifications WHERE event_id = ?", Integer.class, event))
        .isZero();
  }

  @Test
  @DisplayName("An unreadable payment event goes to this service's own dead-letter topic")
  void invalidEventIsDeadLettered() {
    send(
        "payment.events.v1",
        UUID.randomUUID(),
        "PaymentCompleted.v1",
        UUID.randomUUID(),
        "{not json");

    try (KafkaConsumer<String, String> consumer =
        new KafkaConsumer<>(
            Map.of(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG,
                String.join(",", kafkaConnectionDetails.getBootstrapServers()),
                ConsumerConfig.GROUP_ID_CONFIG,
                "notification-it-" + UUID.randomUUID(),
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG,
                "earliest"),
            new StringDeserializer(),
            new StringDeserializer())) {
      consumer.subscribe(List.of("payment.events.v1.notification-service-dlt"));
      List<String> values = new ArrayList<>();
      Awaitility.await()
          .atMost(TIMEOUT)
          .until(() -> {
            consumer.poll(Duration.ofMillis(500)).forEach(r -> values.add(r.value()));
            return values.contains("{not json");
          });
    }
  }

  /** A registered user with an opened account. */
  private Party party(String name) {
    Party party =
        new Party(
            UUID.randomUUID(),
            UUID.randomUUID(),
            name + "-" + UUID.randomUUID() + "@example.com",
            Long.toString(100_000_000_000L + (long) (Math.random() * 899_999_999_999L)));
    register(party);
    return party;
  }

  private void register(Party party) {
    send(
        "auth.user-events.v1",
        party.user,
        "UserRegistered.v1",
        UUID.randomUUID(),
        "{\"userId\":\"%s\",\"email\":\"%s\",\"occurredAt\":\"2026-11-23T09:00:00Z\"}"
            .formatted(party.user, party.email));
    send(
        "account.events.v1",
        party.account,
        "AccountOpened.v1",
        UUID.randomUUID(),
        account(party, party.number));
    Awaitility.await()
        .atMost(TIMEOUT)
        .until(() ->
            jdbcTemplate.queryForObject(
                    "SELECT count(*) FROM known_accounts a JOIN known_users u"
                        + " ON u.user_id = a.owner_user_id WHERE a.account_id = ?",
                    Integer.class,
                    party.account)
                == 1);
  }

  private static String account(Party party, String number) {
    return "{\"accountId\":\"%s\",\"accountNumber\":\"%s\",\"ownerUserId\":\"%s\",\"currency\":\"INR\"}"
        .formatted(party.account, number, party.user);
  }

  private static String payment(
      String type, Party sender, Party beneficiary, String amount, String failureReason) {
    return ("{\"paymentId\":\"%s\",\"type\":\"%s\",\"requestedBy\":%s,\"fromAccountId\":%s,"
            + "\"toAccountId\":\"%s\",\"amount\":\"%s\",\"currency\":\"INR\",\"failureReason\":%s}")
        .formatted(
            UUID.randomUUID(),
            type,
            sender == null ? "\"" + UUID.randomUUID() + "\"" : "\"" + sender.user + "\"",
            sender == null ? "null" : "\"" + sender.account + "\"",
            beneficiary.account,
            amount,
            failureReason == null ? "null" : "\"" + failureReason + "\"");
  }

  private void send(String topic, UUID key, String type, UUID eventId, String value) {
    ProducerRecord<Object, Object> record = new ProducerRecord<>(topic, key.toString(), value);
    headers(record, type, eventId);
    kafkaTemplate.send(record).join();
  }

  private static void headers(ProducerRecord<Object, Object> record, String type, UUID eventId) {
    record.headers().add("eventType", type.getBytes(StandardCharsets.UTF_8));
    record.headers().add("eventId", eventId.toString().getBytes(StandardCharsets.UTF_8));
  }

  private void await(String email, int count) {
    Awaitility.await().atMost(TIMEOUT).until(() -> mailbox.subjectsTo(email).size() >= count);
    // A little longer: a duplicate would show up now.
    Awaitility.await().pollDelay(Duration.ofSeconds(1)).atMost(TIMEOUT).until(() -> true);
    assertThat(mailbox.subjectsTo(email)).hasSize(count);
  }

  private boolean processed(UUID eventId) {
    return jdbcTemplate.queryForObject(
            "SELECT count(*) FROM processed_events WHERE event_id = ?", Integer.class, eventId)
        == 1;
  }

  /** {@code null} before the first attempt (no row yet, or last_error still NULL). */
  private String lastError(UUID eventId) {
    List<String> errors =
        jdbcTemplate.queryForList(
            "SELECT last_error FROM notifications WHERE event_id = ?", String.class, eventId);
    return errors.isEmpty() ? null : errors.getFirst();
  }

  private String statusOf(UUID eventId) {
    return jdbcTemplate.queryForObject(
        "SELECT status FROM notifications WHERE event_id = ?", String.class, eventId);
  }

  private static String last4(Party party) {
    return party.number.substring(party.number.length() - 4);
  }

  private record Party(UUID user, UUID account, String email, String number) {}
}
