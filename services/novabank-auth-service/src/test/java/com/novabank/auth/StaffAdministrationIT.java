package com.novabank.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.novabank.auth.application.port.persistence.EmailVerificationTokenStore;
import com.novabank.auth.application.port.security.PasswordEncoder;
import com.novabank.auth.application.usecase.VerifyEmailUseCase;
import com.novabank.auth.application.verification.VerificationToken;
import com.novabank.auth.domain.model.RoleName;
import com.novabank.auth.domain.model.User;
import com.novabank.auth.domain.repository.UserRepository;
import com.novabank.auth.domain.valueobject.EmailAddress;
import com.novabank.auth.domain.valueobject.PasswordHash;
import com.novabank.auth.support.KafkaTestcontainersConfiguration;
import com.novabank.auth.support.MailpitTestcontainersConfiguration;
import com.novabank.auth.support.TestcontainersConfiguration;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
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

/** Staff roles through the real filter chain, tokens and database (2.10.1b). */
@SpringBootTest(
    properties = {
      "security.jwt.issuer=http://auth.test",
      "security.jwt.generate-ephemeral-key=true",
      "events.user-events.replication-factor=1",
      "verification.email.link-template=http://app.test/verify-email#token={token}",
      "spring.mail.host=localhost",
      "security.rate-limit.enabled=false",
      "security.bootstrap-admin.email=" + StaffAdministrationIT.BOOTSTRAP_EMAIL
    })
@AutoConfigureMockMvc
@Import({
  TestcontainersConfiguration.class,
  KafkaTestcontainersConfiguration.class,
  MailpitTestcontainersConfiguration.class
})
class StaffAdministrationIT {

  static final String BOOTSTRAP_EMAIL = "first-admin@example.com";
  private static final String PASSWORD = "correct horse battery staple";

  @Autowired private MockMvc mockMvc;
  @Autowired private UserRepository users;
  @Autowired private PasswordEncoder passwordEncoder;
  @Autowired private EmailVerificationTokenStore tokens;
  @Autowired private VerifyEmailUseCase verifyEmail;
  @Autowired private JdbcTemplate jdbcTemplate;

  @Test
  @DisplayName("The configured address becomes ADMIN when it is verified, and can then grant roles")
  void bootstrapAdminGrantsAndRevokesStaffRoles() throws Exception {
    User firstAdmin = pendingUser(BOOTSTRAP_EMAIL);
    verify(firstAdmin);
    String adminToken = login(BOOTSTRAP_EMAIL);
    assertThat(roles(adminToken)).contains("ADMIN");

    String staffEmail = activeUser();
    String staffId =
        JsonPath.read(
            mockMvc
                .perform(
                    post("/api/v1/admin/users/lookup")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"%s\"}".formatted(staffEmail)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.roles[0]").value("CUSTOMER"))
                .andReturn()
                .getResponse()
                .getContentAsString(),
            "$.userId");

    // Idempotent: the second PUT is a no-op.
    for (int i = 0; i < 2; i++) {
      mockMvc
          .perform(
              put("/api/v1/admin/users/{id}/roles/OPERATIONS", staffId)
                  .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
          .andExpect(status().isNoContent());
    }
    // Roles travel in the token, so a new sign-in shows them.
    assertThat(roles(login(staffEmail))).containsExactlyInAnyOrder("CUSTOMER", "OPERATIONS");

    mockMvc
        .perform(
            delete("/api/v1/admin/users/{id}/roles/OPERATIONS", staffId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
        .andExpect(status().isNoContent());
    assertThat(roles(login(staffEmail))).containsExactly("CUSTOMER");

    // One event per real change (the repeated PUT published nothing), naming the administrator.
    assertThat(
            jdbcTemplate.queryForList(
                "SELECT event_type FROM outbox_events WHERE aggregate_id = ?::uuid"
                    + " AND event_type LIKE 'StaffRole%' ORDER BY created_at",
                String.class, staffId))
        .containsExactly("StaffRoleGranted.v1", "StaffRoleRevoked.v1");
    assertThat(
            jdbcTemplate.queryForList(
                "SELECT payload->>'changedBy' FROM outbox_events WHERE aggregate_id = ?::uuid"
                    + " AND event_type LIKE 'StaffRole%'",
                String.class, staffId))
        .containsOnly(firstAdmin.id().value().toString());
  }

  @Test
  @DisplayName("Only administrators reach the admin API; CUSTOMER and unknown roles are refused")
  void adminApiIsGuarded() throws Exception {
    String customerToken = login(activeUser());
    mockMvc
        .perform(
            post("/api/v1/admin/users/lookup")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + customerToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"nobody@example.com\"}"))
        .andExpect(status().isForbidden());
    mockMvc
        .perform(put("/api/v1/admin/users/{id}/roles/OPERATIONS", UUID.randomUUID()))
        .andExpect(status().isUnauthorized());

    String adminToken = login(adminUser());
    String someone = UUID.randomUUID().toString();
    mockMvc
        .perform(
            put("/api/v1/admin/users/{id}/roles/CUSTOMER", someone)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
        .andExpect(status().isBadRequest());
    mockMvc
        .perform(
            put("/api/v1/admin/users/{id}/roles/SUPERUSER", someone)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
        .andExpect(status().isBadRequest());
    mockMvc
        .perform(
            put("/api/v1/admin/users/{id}/roles/OPERATIONS", someone)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
        .andExpect(status().isNotFound());
  }

  private User pendingUser(String email) {
    return users.save(
        User.register(EmailAddress.of(email), PasswordHash.of(passwordEncoder.encode(PASSWORD))));
  }

  /** As if the user followed the emailed link. */
  private void verify(User user) {
    VerificationToken token = VerificationToken.generate();
    Instant now = Instant.now();
    tokens.save(user.id().value(), token.hash(), now, now.plus(Duration.ofHours(1)));
    verifyEmail.verify(token.value());
  }

  private String activeUser() {
    String email = "staff-" + UUID.randomUUID() + "@example.com";
    User user = pendingUser(email);
    user.activate();
    users.save(user);
    return email;
  }

  private String adminUser() {
    String email = "admin-" + UUID.randomUUID() + "@example.com";
    User user = pendingUser(email);
    user.activate();
    user.assignRole(RoleName.ADMIN);
    users.save(user);
    return email;
  }

  private String login(String email) throws Exception {
    return JsonPath.read(
        mockMvc
            .perform(
                post("/api/v1/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, PASSWORD)))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString(),
        "$.accessToken");
  }

  private List<String> roles(String token) throws Exception {
    return JsonPath.read(
        mockMvc
            .perform(get("/api/v1/auth/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString(),
        "$.roles");
  }
}
