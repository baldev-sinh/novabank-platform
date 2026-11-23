package com.novabank.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.jayway.jsonpath.JsonPath;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.novabank.auth.support.KafkaTestcontainersConfiguration;
import com.novabank.auth.support.MailpitTestcontainersConfiguration;
import com.novabank.auth.support.TestcontainersConfiguration;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Service tokens (7.2): a client authenticates with a JWT signed by its own key (private_key_jwt),
 * auth checks it against the client's published JWKS and issues a scoped access token.
 */
@SpringBootTest(
    properties = {
      "security.jwt.issuer=" + ServiceTokenIT.ISSUER,
      "security.jwt.generate-ephemeral-key=true",
      "events.user-events.replication-factor=1",
      "verification.email.link-template=http://app.test/verify-email#token={token}",
      "spring.mail.host=localhost",
      "security.rate-limit.enabled=false"
    })
@AutoConfigureMockMvc
@Import({
  TestcontainersConfiguration.class,
  KafkaTestcontainersConfiguration.class,
  MailpitTestcontainersConfiguration.class
})
class ServiceTokenIT {

  static final String ISSUER = "http://auth.test";
  static final String CLIENT = "payment-service";
  private static final String TOKEN_ENDPOINT = ISSUER + "/oauth2/token";

  /** The calling service's key pair; only its public half is published. */
  private static final RSAKey CLIENT_KEY = generateKey();

  private static final HttpServer JWKS_SERVER = startJwksServer();

  @Autowired private MockMvc mockMvc;
  @Autowired private JwtDecoder jwtDecoder;

  /**
   * The whole client in one property source: Spring Boot binds a list from the highest-priority
   * source that has any of its elements, so splitting it would drop the other half.
   */
  @DynamicPropertySource
  static void serviceClient(DynamicPropertyRegistry registry) {
    String prefix = "security.service-clients.clients[0].";
    registry.add(prefix + "client-id", () -> CLIENT);
    registry.add(prefix + "scopes[0]", () -> "accounts:read");
    registry.add(
        prefix + "jwk-set-uri",
        () -> "http://localhost:" + JWKS_SERVER.getAddress().getPort() + "/jwks");
  }

  @AfterAll
  static void stopJwksServer() {
    JWKS_SERVER.stop(0);
  }

  @Test
  @DisplayName("A signed client assertion gets a scoped service token that NovaBank APIs accept")
  void issuesServiceToken() throws Exception {
    MockHttpServletResponse response =
        requestToken(CLIENT, assertion(CLIENT, CLIENT_KEY), "accounts:read");

    assertThat(response.getStatus()).as(response.getContentAsString()).isEqualTo(200);
    String accessToken = JsonPath.read(response.getContentAsString(), "$.access_token");
    // auth's own decoder: same signing key, issuer and audience checks as every resource server.
    Jwt jwt = jwtDecoder.decode(accessToken);
    assertThat(jwt.getSubject()).isEqualTo(CLIENT);
    assertThat(jwt.getAudience()).contains("novabank-api");
    assertThat(jwt.getClaimAsStringList("scope")).containsExactly("accounts:read");
    assertThat(jwt.hasClaim("roles")).isFalse();
    assertThat(jwt.getExpiresAt()).isBefore(Instant.now().plusSeconds(301));
  }

  @Test
  @DisplayName("An assertion signed with another key is refused")
  void wrongKeyIsRefused() throws Exception {
    MockHttpServletResponse response =
        requestToken(CLIENT, assertion(CLIENT, generateKey()), "accounts:read");

    assertThat(response.getStatus()).isEqualTo(401);
    assertThat(response.getContentAsString()).contains("invalid_client");
  }

  @Test
  @DisplayName("Unknown clients and scopes the client wasn't granted are refused")
  void unknownClientOrScopeIsRefused() throws Exception {
    assertThat(
            requestToken(
                    "unknown-service", assertion("unknown-service", CLIENT_KEY), "accounts:read")
                .getStatus())
        .isEqualTo(401);

    MockHttpServletResponse wrongScope =
        requestToken(CLIENT, assertion(CLIENT, CLIENT_KEY), "ledger:write");
    assertThat(wrongScope.getStatus()).isEqualTo(400);
    assertThat(wrongScope.getContentAsString()).contains("invalid_scope");
  }

  private MockHttpServletResponse requestToken(
      String clientId, String clientAssertion, String scope) throws Exception {
    return mockMvc
        .perform(
            post("/oauth2/token")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .param("grant_type", "client_credentials")
                // Spring Authorization Server requires client_id next to the assertion.
                .param("client_id", clientId)
                .param("scope", scope)
                .param(
                    "client_assertion_type",
                    "urn:ietf:params:oauth:client-assertion-type:jwt-bearer")
                .param("client_assertion", clientAssertion))
        .andReturn()
        .getResponse();
  }

  /** RFC 7523 section 3: iss = sub = client id, aud = the authorization server, short expiry. */
  private static String assertion(String clientId, RSAKey key) throws JOSEException {
    Instant now = Instant.now();
    SignedJWT jwt =
        new SignedJWT(
            new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(key.getKeyID()).build(),
            new JWTClaimsSet.Builder()
                .issuer(clientId)
                .subject(clientId)
                .audience(List.of(ISSUER, TOKEN_ENDPOINT))
                .issueTime(Date.from(now))
                .expirationTime(Date.from(now.plusSeconds(60)))
                .jwtID(UUID.randomUUID().toString())
                .build());
    jwt.sign(new RSASSASigner(key));
    return jwt.serialize();
  }

  private static RSAKey generateKey() {
    try {
      return new RSAKeyGenerator(2048).keyID(UUID.randomUUID().toString()).generate();
    } catch (JOSEException ex) {
      throw new IllegalStateException(ex);
    }
  }

  /** Serves the client's public JWKS, the way the calling service does on its own endpoint. */
  private static HttpServer startJwksServer() {
    try {
      HttpServer server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
      byte[] body =
          new JWKSet(CLIENT_KEY.toPublicJWK()).toString().getBytes(StandardCharsets.UTF_8);
      server.createContext(
          "/jwks",
          exchange -> {
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream out = exchange.getResponseBody()) {
              out.write(body);
            }
          });
      server.start();
      return server;
    } catch (IOException ex) {
      throw new IllegalStateException(ex);
    }
  }
}
