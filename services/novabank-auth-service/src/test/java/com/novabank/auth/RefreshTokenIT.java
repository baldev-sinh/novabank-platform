package com.novabank.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.novabank.auth.application.port.security.PasswordEncoder;
import com.novabank.auth.domain.model.User;
import com.novabank.auth.domain.repository.UserRepository;
import com.novabank.auth.domain.valueobject.EmailAddress;
import com.novabank.auth.domain.valueobject.PasswordHash;
import com.novabank.auth.support.KafkaTestcontainersConfiguration;
import com.novabank.auth.support.MailpitTestcontainersConfiguration;
import com.novabank.auth.support.TestcontainersConfiguration;
import jakarta.servlet.http.Cookie;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;

/** Refresh token rotation, reuse detection and logout through the real stack (7.1). */
@SpringBootTest(
    properties = {
      "security.jwt.issuer=http://auth.test",
      "security.jwt.generate-ephemeral-key=true",
      "events.user-events.replication-factor=1",
      "verification.email.link-template=http://app.test/verify-email#token={token}",
      "spring.mail.host=localhost",
      "security.rate-limit.enabled=false",
      // Any reuse counts, so the reuse test doesn't have to wait out the grace period.
      "security.refresh-token.reuse-grace=0s"
    })
@AutoConfigureMockMvc
@Import({
  TestcontainersConfiguration.class,
  KafkaTestcontainersConfiguration.class,
  MailpitTestcontainersConfiguration.class
})
// A lock wait would otherwise hang the build instead of failing it.
@Timeout(value = 60, unit = TimeUnit.SECONDS)
class RefreshTokenIT {

  private static final String PASSWORD = "correct horse battery staple";
  private static final String COOKIE = "nb_refresh";

  @Autowired private MockMvc mockMvc;
  @Autowired private UserRepository users;
  @Autowired private PasswordEncoder passwordEncoder;
  @Autowired private JdbcTemplate jdbcTemplate;

  @Test
  @DisplayName("Login sets an HttpOnly, Secure, SameSite=Strict cookie scoped to the auth API")
  void loginSetsHardenedCookie() throws Exception {
    MockHttpServletResponse response = login(activeUser());

    String setCookie = response.getHeader(HttpHeaders.SET_COOKIE);
    assertThat(setCookie)
        .startsWith(COOKIE + "=")
        .contains("HttpOnly", "Secure", "SameSite=Strict", "Path=/api/v1/auth", "Max-Age=");
    assertThat(setCookie).doesNotContain("Domain=");
    // The refresh token never appears in the body.
    assertThat(response.getContentAsString()).doesNotContain(cookieFrom(response).getValue());
  }

  @Test
  @DisplayName("Refresh rotates: a new access token and cookie; the new cookie works next time")
  void refreshRotates() throws Exception {
    Cookie first = cookieFrom(login(activeUser()));

    MockHttpServletResponse refreshed = refresh(first);
    assertThat(refreshed.getStatus()).isEqualTo(200);
    assertThat((String) JsonPath.read(refreshed.getContentAsString(), "$.accessToken"))
        .isNotBlank();
    Cookie second = cookieFrom(refreshed);
    assertThat(second.getValue()).isNotEqualTo(first.getValue());

    assertThat(refresh(second).getStatus()).isEqualTo(200);
  }

  @Test
  @DisplayName("Reusing a rotated token revokes the sign-in: the latest token stops working too")
  void reuseRevokesTheSignIn() throws Exception {
    Cookie first = cookieFrom(login(activeUser()));
    Cookie second = cookieFrom(refresh(first));

    assertThat(refresh(first).getStatus()).isEqualTo(401);
    assertThat(refresh(second).getStatus()).isEqualTo(401);
  }

  @Test
  @DisplayName("Concurrent refreshes with one token: exactly one succeeds")
  void concurrentRefreshHasOneWinner() throws Exception {
    Cookie cookie = cookieFrom(login(activeUser()));
    CountDownLatch start = new CountDownLatch(1);
    Callable<Integer> attempt =
        () -> {
          start.await();
          return refresh(cookie).getStatus();
        };

    List<Integer> statuses = new ArrayList<>();
    ExecutorService pool = Executors.newFixedThreadPool(5);
    try {
      List<Future<Integer>> futures = new ArrayList<>();
      for (int i = 0; i < 5; i++) {
        futures.add(pool.submit(attempt));
      }
      start.countDown();
      for (Future<Integer> future : futures) {
        statuses.add(future.get(30, TimeUnit.SECONDS));
      }
    } finally {
      // Never wait forever on a stuck request: fail the test instead.
      pool.shutdownNow();
    }

    assertThat(statuses).filteredOn(code -> code == 200).hasSize(1);
  }

  @Test
  @DisplayName("Refresh and logout need X-Requested-With (CSRF guard); no cookie is a 401")
  void csrfHeaderAndCookieRequired() throws Exception {
    Cookie cookie = cookieFrom(login(activeUser()));

    mockMvc.perform(post("/api/v1/auth/refresh").cookie(cookie)).andExpect(status().isForbidden());
    mockMvc.perform(post("/api/v1/auth/logout").cookie(cookie)).andExpect(status().isForbidden());
    mockMvc
        .perform(post("/api/v1/auth/refresh").header("X-Requested-With", "XMLHttpRequest"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.detail").value("Your session has ended. Please sign in again."));
  }

  @Test
  @DisplayName("Logout clears the cookie and ends the sign-in; doing it twice is fine")
  void logoutEndsTheSignIn() throws Exception {
    Cookie cookie = cookieFrom(login(activeUser()));

    MockHttpServletResponse out =
        mockMvc
            .perform(
                post("/api/v1/auth/logout")
                    .cookie(cookie)
                    .header("X-Requested-With", "XMLHttpRequest"))
            .andExpect(status().isNoContent())
            .andReturn()
            .getResponse();
    assertThat(out.getHeader(HttpHeaders.SET_COOKIE)).contains(COOKIE + "=", "Max-Age=0");

    assertThat(refresh(cookie).getStatus()).isEqualTo(401);
    mockMvc
        .perform(
            post("/api/v1/auth/logout").cookie(cookie).header("X-Requested-With", "XMLHttpRequest"))
        .andExpect(status().isNoContent());
  }

  @Test
  @DisplayName("A user locked after signing in can't refresh, and the sign-in is revoked")
  void lockedUserCannotRefresh() throws Exception {
    String email = activeUser();
    Cookie cookie = cookieFrom(login(email));
    User user = users.findByEmail(EmailAddress.of(email)).orElseThrow();
    user.lock();
    users.save(user);

    assertThat(refresh(cookie).getStatus()).isEqualTo(401);

    Integer live =
        jdbcTemplate.queryForObject(
            "SELECT count(*) FROM refresh_tokens WHERE user_id = ? AND revoked_at IS NULL",
            Integer.class,
            user.id().value());
    assertThat(live).isZero();
  }

  @Test
  @DisplayName("Only the token's hash is stored")
  void onlyHashIsStored() throws Exception {
    Cookie cookie = cookieFrom(login(activeUser()));

    Integer plain =
        jdbcTemplate.queryForObject(
            "SELECT count(*) FROM refresh_tokens WHERE token_hash = ?",
            Integer.class,
            cookie.getValue());
    assertThat(plain).isZero();
  }

  @Test
  @DisplayName("The access token from a refresh is accepted by the API")
  void refreshedAccessTokenWorks() throws Exception {
    MockHttpServletResponse refreshed = refresh(cookieFrom(login(activeUser())));
    String accessToken = JsonPath.read(refreshed.getContentAsString(), "$.accessToken");

    mockMvc
        .perform(get("/api/v1/auth/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
        .andExpect(status().isOk());
  }

  private MockHttpServletResponse refresh(Cookie cookie) throws Exception {
    return mockMvc
        .perform(
            post("/api/v1/auth/refresh")
                .cookie(cookie)
                .header("X-Requested-With", "XMLHttpRequest"))
        .andReturn()
        .getResponse();
  }

  private MockHttpServletResponse login(String email) throws Exception {
    return mockMvc
        .perform(
            post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, PASSWORD)))
        .andExpect(status().isOk())
        .andReturn()
        .getResponse();
  }

  private static Cookie cookieFrom(MockHttpServletResponse response) {
    Cookie cookie = response.getCookie(COOKIE);
    assertThat(cookie).as("refresh token cookie").isNotNull();
    return cookie;
  }

  private String activeUser() {
    String email = "refresh-" + UUID.randomUUID() + "@example.com";
    User user =
        User.register(EmailAddress.of(email), PasswordHash.of(passwordEncoder.encode(PASSWORD)));
    user.activate();
    users.save(user);
    return email;
  }
}
