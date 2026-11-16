package com.novabank.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.novabank.auth.application.exception.InvalidVerificationTokenException;
import com.novabank.auth.application.usecase.VerifyEmailUseCase;
import com.novabank.auth.application.verification.VerificationToken;
import com.novabank.auth.support.KafkaTestcontainersConfiguration;
import com.novabank.auth.support.MailpitTestcontainersConfiguration;
import com.novabank.auth.support.MailpitTestcontainersConfiguration.Mailbox;
import com.novabank.auth.support.TestcontainersConfiguration;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.kafka.autoconfigure.KafkaConnectionDetails;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/** End-to-end through the real filter chain, controllers, services, Flyway schema and database. */
@SpringBootTest(
    properties = {
      "security.jwt.issuer=http://auth.test",
      "security.jwt.generate-ephemeral-key=true",
      "events.user-events.replication-factor=1",
      "novabank.outbox.relay.poll-interval-ms=200",
      "verification.email.link-template=http://app.test/verify-email#token={token}",
      // The cooldown is covered by ResendVerificationEmailServiceTest.
      "verification.email.resend-cooldown=0s",
      // Needed by the mail auto-config condition; Mailpit's dynamic properties replace it.
      "spring.mail.host=localhost"
    })
@AutoConfigureMockMvc
@Import({
  TestcontainersConfiguration.class,
  KafkaTestcontainersConfiguration.class,
  MailpitTestcontainersConfiguration.class
})
class AuthenticationFlowIT {

  private static final String PASSWORD = "correct horse battery staple";
  private static final Pattern TOKEN_IN_LINK =
      Pattern.compile("http://app\\.test/verify-email#token=([A-Za-z0-9_-]+)");

  @Autowired private MockMvc mockMvc;

  @Autowired private JdbcTemplate jdbcTemplate;

  @Autowired private Mailbox mailbox;

  @Autowired private VerifyEmailUseCase verifyEmailUseCase;

  /** Set by @ServiceConnection from the Kafka container (not a spring.kafka.* property). */
  @Autowired private KafkaConnectionDetails kafkaConnectionDetails;

  @Value("${events.user-events.topic}")
  private String topic;

  @Test
  @DisplayName("Register, verify the emailed link, log in, and call /me with the issued token")
  void registerVerifyLoginAndCallMe() throws Exception {
    String email = "flow@example.com";

    mockMvc
        .perform(
            post("/api/v1/users/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(credentials(email, PASSWORD)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.email").value(email))
        .andExpect(jsonPath("$.status").value("PENDING_VERIFICATION"));

    // Not verified yet: a distinct 403 (only after a correct password).
    mockMvc
        .perform(
            post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(credentials(email, PASSWORD)))
        .andExpect(status().isForbidden())
        .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.type").value("urn:novabank:problem:email-not-verified"));

    verify(awaitVerificationToken(email, 1)).andExpect(status().isNoContent());

    String token = login(email, PASSWORD);

    mockMvc
        .perform(get("/api/v1/auth/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.email").value(email))
        .andExpect(jsonPath("$.roles[0]").value("CUSTOMER"));
  }

  @Test
  @DisplayName("Registering the same email twice returns 409")
  void duplicateRegistrationReturns409() throws Exception {
    register("dup@example.com");

    mockMvc
        .perform(
            post("/api/v1/users/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(credentials("dup@example.com", PASSWORD)))
        .andExpect(status().isConflict())
        .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON));
  }

  @Test
  @DisplayName("A weak password is rejected with 400")
  void weakPasswordReturns400() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/users/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(credentials("weak@example.com", "Password@123")))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors[0].field").value("password"));
  }

  @Test
  @DisplayName("Wrong password and unknown email get the same 401")
  void badCredentialsReturn401() throws Exception {
    register("known@example.com");

    for (String body :
        new String[] {
          credentials("known@example.com", "wrong password value"),
          credentials("nobody@example.com", PASSWORD)
        }) {
      mockMvc
          .perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content(body))
          .andExpect(status().isUnauthorized())
          .andExpect(jsonPath("$.detail").value("Invalid email or password."));
    }
  }

  @Test
  @DisplayName("Protected endpoints reject missing and tampered tokens with 401")
  void protectedEndpointRejectsBadTokens() throws Exception {
    mockMvc
        .perform(get("/api/v1/auth/me"))
        .andExpect(status().isUnauthorized())
        .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, "Bearer"));

    registerAndVerify("tamper@example.com");
    String token = login("tamper@example.com", PASSWORD);
    String tampered = token.substring(0, token.length() - 4) + "AAAA";

    mockMvc
        .perform(get("/api/v1/auth/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + tampered))
        .andExpect(status().isUnauthorized());
  }

  @Test
  @DisplayName("The JWKS endpoint is public and exposes only the public key")
  void jwksIsPublicAndHasNoPrivateKey() throws Exception {
    mockMvc
        .perform(get("/.well-known/jwks.json"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.keys[0].kty").value("RSA"))
        .andExpect(jsonPath("$.keys[0].kid").isNotEmpty())
        .andExpect(jsonPath("$.keys[0]", not(hasKey("d"))));
  }

  @Test
  @DisplayName("Registration publishes UserRegistered to Kafka through the outbox")
  void registrationPublishesEventThroughOutbox() throws Exception {
    String email = "outbox@example.com";
    String userId =
        JsonPath.read(
            mockMvc
                .perform(
                    post("/api/v1/users/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(credentials(email, PASSWORD)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString(),
            "$.userId");

    ConsumerRecord<String, String> event = awaitEventFor(userId);

    assertThat(new String(event.headers().lastHeader("eventType").value(), StandardCharsets.UTF_8))
        .isEqualTo("UserRegistered.v1");
    String eventId =
        new String(event.headers().lastHeader("eventId").value(), StandardCharsets.UTF_8);
    assertThat((String) JsonPath.read(event.value(), "$.eventId")).isEqualTo(eventId);
    assertThat((String) JsonPath.read(event.value(), "$.email")).isEqualTo(email);
    assertThat(event.value()).doesNotContain("password", "$2a$");

    // The relay marks the row once Kafka has acknowledged it.
    Awaitility.await()
        .atMost(Duration.ofSeconds(10))
        .until(() ->
            jdbcTemplate.queryForObject(
                "SELECT published_at IS NOT NULL FROM outbox_events WHERE id = ?::uuid",
                Boolean.class,
                eventId));
  }

  @Test
  @DisplayName("A rejected registration writes no event")
  void rejectedRegistrationWritesNoEvent() throws Exception {
    register("once@example.com");
    Integer before =
        jdbcTemplate.queryForObject("SELECT count(*) FROM outbox_events", Integer.class);

    mockMvc
        .perform(
            post("/api/v1/users/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(credentials("once@example.com", PASSWORD)))
        .andExpect(status().isConflict());

    assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM outbox_events", Integer.class))
        .isEqualTo(before);
  }

  @Test
  @DisplayName("A verification token works once, and only its hash is stored")
  void verificationTokenIsSingleUseAndHashed() throws Exception {
    register("once-only@example.com");
    String token = awaitVerificationToken("once-only@example.com", 1);

    assertThat(
            jdbcTemplate.queryForList(
                "SELECT token_hash FROM email_verification_tokens t JOIN users u"
                    + " ON u.id = t.user_id WHERE u.email = ?",
                String.class,
                "once-only@example.com"))
        .containsExactly(VerificationToken.hashOf(token))
        .doesNotContain(token);

    verify(token).andExpect(status().isNoContent());
    verify(token)
        .andExpect(status().isBadRequest())
        .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.detail").value("The verification link is invalid or has expired."));
    verify("not-a-real-token").andExpect(status().isBadRequest());
  }

  @Test
  @DisplayName("An expired verification token is rejected")
  void expiredTokenIsRejected() throws Exception {
    register("late@example.com");
    String token = awaitVerificationToken("late@example.com", 1);
    jdbcTemplate.update(
        "UPDATE email_verification_tokens SET expires_at = now() - interval '1 second'"
            + " WHERE token_hash = ?",
        VerificationToken.hashOf(token));

    verify(token).andExpect(status().isBadRequest());
  }

  @Test
  @DisplayName("Resend replaces the old link; unknown addresses get the same 202")
  void resendReplacesOldToken() throws Exception {
    register("resend@example.com");
    String first = awaitVerificationToken("resend@example.com", 1);

    resend("resend@example.com").andExpect(status().isAccepted());
    resend("nobody-here@example.com").andExpect(status().isAccepted());
    String second = awaitVerificationToken("resend@example.com", 2);

    assertThat(second).isNotEqualTo(first);
    verify(first).andExpect(status().isBadRequest());
    verify(second).andExpect(status().isNoContent());
    assertThat(mailbox.textsTo("nobody-here@example.com")).isEmpty();
  }

  @Test
  @DisplayName("Two concurrent verifications with the same token: exactly one succeeds")
  void concurrentVerificationSucceedsOnce() throws Exception {
    register("race@example.com");
    String token = awaitVerificationToken("race@example.com", 1);

    Callable<Boolean> attempt =
        () -> {
          try {
            verifyEmailUseCase.verify(token);
            return true;
          } catch (InvalidVerificationTokenException ex) {
            return false;
          }
        };
    try (ExecutorService pool = Executors.newFixedThreadPool(2)) {
      List<Future<Boolean>> results = pool.invokeAll(List.of(attempt, attempt));
      assertThat(results.stream().filter(AuthenticationFlowIT::succeeded).count()).isOne();
    }
  }

  private static boolean succeeded(Future<Boolean> result) {
    try {
      return result.get();
    } catch (Exception ex) {
      throw new AssertionError(ex);
    }
  }

  private ConsumerRecord<String, String> awaitEventFor(String userId) {
    Map<String, Object> config =
        Map.of(
            ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG,
            String.join(",", kafkaConnectionDetails.getBootstrapServers()),
            ConsumerConfig.GROUP_ID_CONFIG,
            "it-" + UUID.randomUUID(),
            ConsumerConfig.AUTO_OFFSET_RESET_CONFIG,
            "earliest",
            ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG,
            StringDeserializer.class,
            ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG,
            StringDeserializer.class);
    try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(config)) {
      consumer.subscribe(List.of(topic));
      long deadline = System.currentTimeMillis() + 20_000;
      while (System.currentTimeMillis() < deadline) {
        for (ConsumerRecord<String, String> consumed : consumer.poll(Duration.ofMillis(500))) {
          if (userId.equals(consumed.key())) {
            return consumed;
          }
        }
      }
    }
    throw new AssertionError("No event for user " + userId + " on " + topic);
  }

  private void register(String email) throws Exception {
    mockMvc
        .perform(
            post("/api/v1/users/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(credentials(email, PASSWORD)))
        .andExpect(status().isCreated());
  }

  private void registerAndVerify(String email) throws Exception {
    register(email);
    verify(awaitVerificationToken(email, 1)).andExpect(status().isNoContent());
  }

  /** Emails are sent asynchronously after commit, so wait for the expected number to arrive. */
  private String awaitVerificationToken(String email, int expectedEmails) {
    List<String> texts =
        Awaitility.await()
            .atMost(Duration.ofSeconds(15))
            .until(() -> mailbox.textsTo(email), list -> list.size() >= expectedEmails);
    Matcher matcher = TOKEN_IN_LINK.matcher(texts.getFirst());
    assertThat(matcher.find()).as("verification link in email").isTrue();
    return matcher.group(1);
  }

  private ResultActions verify(String token) throws Exception {
    return mockMvc.perform(
        post("/api/v1/users/verify-email")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"token\":\"%s\"}".formatted(token)));
  }

  private ResultActions resend(String email) throws Exception {
    return mockMvc.perform(
        post("/api/v1/users/verify-email/resend")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"email\":\"%s\"}".formatted(email)));
  }

  private String login(String email, String password) throws Exception {
    String body =
        mockMvc
            .perform(
                post("/api/v1/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(credentials(email, password)))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return JsonPath.read(body, "$.accessToken");
  }

  private static String credentials(String email, String password) {
    return "{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, password);
  }
}
