package com.novabank.payment.infrastructure.accounts;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.nimbusds.jose.crypto.RSASSAVerifier;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jwt.SignedJWT;
import com.novabank.payment.application.exception.AccountServiceUnavailableException;
import com.novabank.payment.application.port.AccountDirectory.AccountSnapshot;
import com.novabank.payment.infrastructure.accounts.AccountDirectoryConfiguration.AccountServiceProperties;
import com.novabank.payment.infrastructure.accounts.ServiceIdentityConfiguration.ServiceIdentityProperties;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.client.InMemoryOAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.web.client.RestClient;

/**
 * The real client against a fake auth (token endpoint) and a fake account service: the service
 * token is obtained with a signed client assertion and cached, the call carries it, a 404 is an
 * answer and repeated failures open the circuit.
 */
class HttpAccountDirectoryTest {

  private static final String ISSUER = "http://auth.test";

  private HttpServer server;
  private String baseUrl;
  private final AtomicInteger tokenRequests = new AtomicInteger();
  private final List<String> assertions = new CopyOnWriteArrayList<>();
  private final List<String> authorizations = new CopyOnWriteArrayList<>();
  private volatile int accountStatusCode = 200;

  private RSAKey serviceKey;
  private HttpAccountDirectory directory;

  @BeforeEach
  void start() throws Exception {
    server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
    server.createContext("/oauth2/token", this::token);
    server.createContext("/api/v1/internal/accounts/", this::account);
    server.start();
    baseUrl = "http://localhost:" + server.getAddress().getPort();

    ServiceIdentityConfiguration identity = new ServiceIdentityConfiguration();
    ServiceIdentityProperties identityProperties =
        new ServiceIdentityProperties(null, null, true, ISSUER);
    serviceKey = identity.serviceSigningKey(identityProperties);
    ClientRegistration registration =
        ClientRegistration.withRegistrationId(HttpAccountDirectory.CLIENT_REGISTRATION)
            .clientId("payment-service")
            .authorizationGrantType(AuthorizationGrantType.CLIENT_CREDENTIALS)
            .clientAuthenticationMethod(ClientAuthenticationMethod.PRIVATE_KEY_JWT)
            .scope("accounts:read")
            .tokenUri(baseUrl + "/oauth2/token")
            .build();
    InMemoryClientRegistrationRepository registrations =
        new InMemoryClientRegistrationRepository(registration);
    OAuth2AuthorizedClientManager manager =
        identity.serviceTokenManager(
            registrations,
            new InMemoryOAuth2AuthorizedClientService(registrations),
            serviceKey,
            identityProperties);

    AccountDirectoryConfiguration accounts = new AccountDirectoryConfiguration();
    AccountServiceProperties properties =
        new AccountServiceProperties(
            baseUrl, Duration.ofSeconds(1), Duration.ofSeconds(2), 50, 4, Duration.ofMinutes(1));
    directory =
        (HttpAccountDirectory)
            accounts.accountDirectory(
                RestClient.builder(),
                manager,
                accounts.circuitBreakerRegistry(properties, new SimpleMeterRegistry()),
                properties);
  }

  @AfterEach
  void stop() {
    server.stop(0);
  }

  @Test
  @DisplayName("Gets a service token with a signed assertion, sends it, and caches it")
  void callsWithCachedServiceToken() throws Exception {
    UUID account = UUID.randomUUID();

    Optional<AccountSnapshot> first = directory.find(account);
    directory.find(account);

    assertThat(first).hasValueSatisfying(a -> assertThat(a.isActive()).isTrue());
    assertThat(tokenRequests).hasValue(1);
    assertThat(authorizations).containsOnly("Bearer service-token");
    SignedJWT assertion = SignedJWT.parse(assertions.getFirst());
    assertThat(assertion.verify(new RSASSAVerifier(serviceKey.toRSAPublicKey()))).isTrue();
    assertThat(assertion.getJWTClaimsSet().getSubject()).isEqualTo("payment-service");
    assertThat(assertion.getJWTClaimsSet().getAudience()).contains(ISSUER);
  }

  @Test
  @DisplayName("A 404 means the account doesn't exist")
  void notFoundIsEmpty() {
    accountStatusCode = 404;

    assertThat(directory.find(UUID.randomUUID())).isEmpty();
  }

  @Test
  @DisplayName("Repeated 5xx open the circuit: later calls fail fast without reaching the service")
  void failuresOpenTheCircuit() {
    accountStatusCode = 503;
    for (int i = 0; i < 4; i++) {
      assertThatThrownBy(() -> directory.find(UUID.randomUUID()))
          .isInstanceOf(AccountServiceUnavailableException.class);
    }
    int callsBefore = authorizations.size();

    assertThatThrownBy(() -> directory.find(UUID.randomUUID()))
        .isInstanceOf(AccountServiceUnavailableException.class)
        .hasMessageContaining("circuit is open");
    assertThat(authorizations).hasSize(callsBefore);
  }

  private void token(HttpExchange exchange) throws IOException {
    tokenRequests.incrementAndGet();
    Map<String, String> form =
        Arrays.stream(
                new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8)
                    .split("&"))
            .map(pair -> pair.split("=", 2))
            .collect(
                Collectors.toMap(
                    pair -> pair[0],
                    pair -> URLDecoder.decode(pair[1], StandardCharsets.UTF_8),
                    (a, b) -> a));
    assertions.add(form.get("client_assertion"));
    respond(
        exchange,
        200,
        "{\"access_token\":\"service-token\",\"token_type\":\"Bearer\",\"expires_in\":300,"
            + "\"scope\":\"accounts:read\"}");
  }

  private void account(HttpExchange exchange) throws IOException {
    authorizations.add(exchange.getRequestHeaders().getFirst("Authorization"));
    String id = exchange.getRequestURI().getPath().replace("/api/v1/internal/accounts/", "");
    if (accountStatusCode != 200) {
      respond(exchange, accountStatusCode, "{}");
      return;
    }
    respond(
        exchange,
        200,
        "{\"accountId\":\"%s\",\"status\":\"ACTIVE\",\"currency\":\"INR\"}".formatted(id));
  }

  private static void respond(HttpExchange exchange, int status, String json) throws IOException {
    byte[] body = json.getBytes(StandardCharsets.UTF_8);
    exchange.getResponseHeaders().add("Content-Type", "application/json");
    exchange.sendResponseHeaders(status, body.length);
    try (OutputStream out = exchange.getResponseBody()) {
      out.write(body);
    }
  }
}
