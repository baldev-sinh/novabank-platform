package com.novabank.customer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.novabank.customer.support.KafkaTestcontainersConfiguration;
import com.novabank.customer.support.TestcontainersConfiguration;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.kafka.autoconfigure.KafkaConnectionDetails;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/** KYC review end to end: queue, approval, audit record, CustomerVerified on Kafka. */
@SpringBootTest(
    properties = {
      "security.jwt.jwk-set-uri=http://localhost:1/not-used-in-this-test",
      "security.jwt.issuer=http://auth.test",
      "security.jwt.audience=novabank-api",
      "events.dead-letter-replication-factor=1",
      "events.customer-events.replication-factor=1"
    })
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, KafkaTestcontainersConfiguration.class})
class KycApprovalIT {

  private static final String TOPIC = "customer.events.v1";
  private static final String PROFILE =
      "{\"firstName\":\"Kyc\",\"lastName\":\"Tester\","
          + "\"dateOfBirth\":\"1990-05-20\",\"phone\":\"+91 91234 56789\"}";

  @Autowired private MockMvc mockMvc;
  @Autowired private JdbcTemplate jdbcTemplate;
  @Autowired private KafkaConnectionDetails kafkaConnectionDetails;

  @Test
  @DisplayName(
      "An officer approves a pending customer: ACTIVE, audited, CustomerVerified published")
  void approvesPendingCustomer() throws Exception {
    UUID userId = UUID.randomUUID();
    String customerId = registerCustomer(userId);
    UUID officerId = UUID.randomUUID();

    mockMvc
        .perform(get("/api/v1/compliance/customers/pending-kyc").with(officer(officerId)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[?(@.customerId == '" + customerId + "')]").exists());

    mockMvc
        .perform(approve(customerId).with(officer(officerId)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("ACTIVE"));

    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT decided_by FROM kyc_decisions WHERE customer_id = ?::uuid",
                UUID.class,
                customerId))
        .isEqualTo(officerId);

    ConsumerRecord<String, String> event = awaitEventFor(customerId);
    assertThat(header(event, "eventType")).isEqualTo("CustomerVerified.v1");
    assertThat((String) JsonPath.read(event.value(), "$.userId")).isEqualTo(userId.toString());
    assertThat((String) JsonPath.read(event.value(), "$.verifiedBy"))
        .isEqualTo(officerId.toString());
    // Ids only: no personal data in events.
    assertThat(event.value()).doesNotContain("Kyc", "Tester", "1990-05-20", "91234");

    mockMvc
        .perform(get("/api/v1/compliance/customers/pending-kyc").with(officer(officerId)))
        .andExpect(jsonPath("$[?(@.customerId == '" + customerId + "')]").doesNotExist());
  }

  @Test
  @DisplayName("A retried approval returns 200 and writes no second event or decision")
  void retryIsIdempotent() throws Exception {
    String customerId = registerCustomer(UUID.randomUUID());
    UUID officerId = UUID.randomUUID();

    mockMvc.perform(approve(customerId).with(officer(officerId))).andExpect(status().isOk());
    mockMvc
        .perform(approve(customerId).with(officer(officerId)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("ACTIVE"));

    assertThat(count("SELECT count(*) FROM outbox_events WHERE aggregate_id = ?::uuid", customerId))
        .isOne();
    assertThat(count("SELECT count(*) FROM kyc_decisions WHERE customer_id = ?::uuid", customerId))
        .isOne();
  }

  @Test
  @DisplayName("Customers can't use compliance endpoints; unknown customers are 404")
  void accessRulesAndNotFound() throws Exception {
    UUID userId = UUID.randomUUID();
    String customerId = registerCustomer(userId);

    mockMvc.perform(approve(customerId).with(customer(userId))).andExpect(status().isForbidden());
    mockMvc
        .perform(get("/api/v1/compliance/customers/pending-kyc").with(customer(userId)))
        .andExpect(status().isForbidden());
    mockMvc
        .perform(approve(UUID.randomUUID().toString()).with(officer(UUID.randomUUID())))
        .andExpect(status().isNotFound());
  }

  private String registerCustomer(UUID userId) throws Exception {
    String body =
        mockMvc
            .perform(
                post("/api/v1/customers")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(PROFILE)
                    .with(customer(userId)))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return JsonPath.read(body, "$.customerId");
  }

  private static MockHttpServletRequestBuilder approve(String customerId) {
    return post("/api/v1/compliance/customers/{id}/kyc-approval", customerId);
  }

  private Integer count(String sql, String id) {
    return jdbcTemplate.queryForObject(sql, Integer.class, id);
  }

  private ConsumerRecord<String, String> awaitEventFor(String customerId) {
    Map<String, Object> config =
        Map.of(
            ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG,
            String.join(",", kafkaConnectionDetails.getBootstrapServers()),
            ConsumerConfig.GROUP_ID_CONFIG,
            "kyc-it-" + UUID.randomUUID(),
            ConsumerConfig.AUTO_OFFSET_RESET_CONFIG,
            "earliest",
            ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG,
            StringDeserializer.class,
            ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG,
            StringDeserializer.class);
    try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(config)) {
      consumer.subscribe(List.of(TOPIC));
      long deadline = System.currentTimeMillis() + 20_000;
      while (System.currentTimeMillis() < deadline) {
        for (ConsumerRecord<String, String> consumed : consumer.poll(Duration.ofMillis(500))) {
          if (customerId.equals(consumed.key())) {
            return consumed;
          }
        }
      }
    }
    throw new AssertionError("No event for customer " + customerId + " on " + TOPIC);
  }

  private static String header(ConsumerRecord<String, String> consumed, String name) {
    return new String(consumed.headers().lastHeader(name).value(), StandardCharsets.UTF_8);
  }

  private static RequestPostProcessor customer(UUID userId) {
    return jwt()
        .jwt(token -> token.subject(userId.toString()))
        .authorities(new SimpleGrantedAuthority("ROLE_CUSTOMER"));
  }

  private static RequestPostProcessor officer(UUID officerId) {
    return jwt()
        .jwt(token -> token.subject(officerId.toString()))
        .authorities(new SimpleGrantedAuthority("ROLE_COMPLIANCE_OFFICER"));
  }
}
