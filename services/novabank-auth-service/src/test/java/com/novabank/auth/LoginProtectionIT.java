package com.novabank.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.novabank.auth.application.port.security.PasswordEncoder;
import com.novabank.auth.domain.model.User;
import com.novabank.auth.domain.repository.UserRepository;
import com.novabank.auth.domain.valueobject.EmailAddress;
import com.novabank.auth.domain.valueobject.PasswordHash;
import com.novabank.auth.support.KafkaTestcontainersConfiguration;
import com.novabank.auth.support.MailpitTestcontainersConfiguration;
import com.novabank.auth.support.TestcontainersConfiguration;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/** Per-IP rate limits and per-account lockout through the real filter chain and database. */
@SpringBootTest(
    properties = {
      "security.jwt.issuer=http://auth.test",
      "security.jwt.generate-ephemeral-key=true",
      "events.user-events.replication-factor=1",
      "verification.email.link-template=http://app.test/verify-email#token={token}",
      "spring.mail.host=localhost",
      "security.rate-limit.rules[0].name=login",
      "security.rate-limit.rules[0].path=/api/v1/auth/login",
      "security.rate-limit.rules[0].capacity=3",
      "security.rate-limit.rules[0].period=1h"
    })
@AutoConfigureMockMvc
@Import({
  TestcontainersConfiguration.class,
  KafkaTestcontainersConfiguration.class,
  MailpitTestcontainersConfiguration.class
})
class LoginProtectionIT {

  private static final String PASSWORD = "correct horse battery staple";

  /** Each test uses fresh addresses, so buckets never leak between tests. */
  private static final AtomicInteger NEXT_IP = new AtomicInteger(1);

  @Autowired private MockMvc mockMvc;
  @Autowired private UserRepository users;
  @Autowired private PasswordEncoder passwordEncoder;
  @Autowired private JdbcTemplate jdbcTemplate;

  @Test
  @DisplayName("The 4th sign-in from one IP within the hour gets 429 with Retry-After")
  void loginIsRateLimitedPerIp() throws Exception {
    String ip = nextIp();
    for (int i = 0; i < 3; i++) {
      login(ip, "nobody@example.com", PASSWORD).andExpect(status().isUnauthorized());
    }

    login(ip, "nobody@example.com", PASSWORD)
        .andExpect(status().isTooManyRequests())
        .andExpect(header().exists("Retry-After"))
        .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.type").value("urn:novabank:problem:rate-limited"))
        .andExpect(jsonPath("$.status").value(429));

    // Another client is not affected.
    login(nextIp(), "nobody@example.com", PASSWORD).andExpect(status().isUnauthorized());

    long retryAfter =
        Long.parseLong(
            login(ip, "nobody@example.com", PASSWORD)
                .andReturn()
                .getResponse()
                .getHeader("Retry-After"));
    // Refill is 3 per hour, so the next token comes within 20 minutes.
    assertThat(retryAfter).isBetween(1L, 1200L);
  }

  @Test
  @DisplayName("5 wrong passwords lock the account: even the right one then gets the same 401")
  void accountLocksAfterFiveFailures() throws Exception {
    String email = activeUser();

    // Different IPs, as in a distributed attack: the per-IP limit alone would not stop it.
    for (int i = 0; i < 5; i++) {
      login(nextIp(), email, "wrong password " + i)
          .andExpect(status().isUnauthorized())
          .andExpect(jsonPath("$.detail").value("Invalid email or password."));
    }

    login(nextIp(), email, PASSWORD)
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.detail").value("Invalid email or password."));

    Boolean locked =
        jdbcTemplate.queryForObject(
            "SELECT locked_until > now() FROM login_attempts la JOIN users u ON u.id = la.user_id"
                + " WHERE u.email = ?",
            Boolean.class,
            email);
    assertThat(locked).isTrue();
  }

  @Test
  @DisplayName("A successful sign-in clears earlier failures")
  void successClearsFailures() throws Exception {
    String email = activeUser();
    for (int i = 0; i < 4; i++) {
      login(nextIp(), email, "wrong password " + i).andExpect(status().isUnauthorized());
    }

    login(nextIp(), email, PASSWORD).andExpect(status().isOk());

    Integer rows =
        jdbcTemplate.queryForObject(
            "SELECT count(*) FROM login_attempts la JOIN users u ON u.id = la.user_id"
                + " WHERE u.email = ?",
            Integer.class,
            email);
    assertThat(rows).isZero();
  }

  @Test
  @DisplayName("CORS: with no origins configured (the default) no cross-origin call is allowed")
  void corsAllowsNoOriginByDefault() throws Exception {
    mockMvc
        .perform(
            options("/api/v1/auth/login")
                .header(HttpHeaders.ORIGIN, "http://localhost:4200")
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST"))
        .andExpect(status().isForbidden());
  }

  private String activeUser() {
    String email = "protect-" + NEXT_IP.get() + "-" + System.nanoTime() + "@example.com";
    User user =
        User.register(EmailAddress.of(email), PasswordHash.of(passwordEncoder.encode(PASSWORD)));
    user.activate();
    users.save(user);
    return email;
  }

  private ResultActions login(String ip, String email, String password) throws Exception {
    return mockMvc.perform(
        post("/api/v1/auth/login")
            .with(request -> {
              request.setRemoteAddr(ip);
              return request;
            })
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, password)));
  }

  private static String nextIp() {
    int n = NEXT_IP.getAndIncrement();
    return "198.51.100." + n;
  }
}
