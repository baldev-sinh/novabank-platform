package com.novabank.auth.infrastructure.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.nimbusds.jose.jwk.RSAKey;
import com.novabank.auth.application.security.AuthenticatedUser;
import com.novabank.auth.domain.model.RoleName;
import com.novabank.auth.infrastructure.configuration.JwtKeyConfiguration;
import com.novabank.auth.infrastructure.configuration.JwtProperties;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.EnumSet;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtException;

class JwtTokenServiceTest {

  private static final String ISSUER = "http://localhost:8081";
  private static final String AUDIENCE = "novabank-api";
  private static final UUID USER_ID = UUID.fromString("7f1c2d1e-8c55-4c4f-9a7e-2b5c8d9e0f11");

  private final JwtKeyConfiguration keys = new JwtKeyConfiguration();
  private final JwtProperties properties = properties(ISSUER, AUDIENCE);

  private RSAKey signingKey;
  private JwtEncoder encoder;
  private JwtDecoder decoder;

  @BeforeEach
  void setUp() throws Exception {
    signingKey = keys.jwtSigningKey(properties);
    encoder = keys.jwtEncoder(signingKey);
    decoder = keys.jwtDecoder(signingKey, properties);
  }

  @Test
  @DisplayName("Issues an RS256 token with the standard and NovaBank claims")
  void issuesTokenWithExpectedClaims() {
    String token = service(Clock.systemUTC()).generateAccessToken(user());

    Jwt jwt = decoder.decode(token);
    assertThat(jwt.getHeaders()).containsEntry("alg", "RS256");
    assertThat(jwt.getHeaders()).containsEntry("kid", signingKey.getKeyID());
    assertThat(jwt.getIssuer()).hasToString(ISSUER);
    assertThat(jwt.getAudience()).containsExactly(AUDIENCE);
    assertThat(jwt.getSubject()).isEqualTo(USER_ID.toString());
    assertThat(jwt.getId()).isNotBlank();
    assertThat(jwt.getClaimAsString(JwtClaims.EMAIL)).isEqualTo("baldev@example.com");
    assertThat(jwt.getClaimAsStringList(JwtClaims.ROLES)).containsExactly("ADMIN", "CUSTOMER");
    assertThat(Duration.between(jwt.getIssuedAt(), jwt.getExpiresAt()))
        .isEqualTo(Duration.ofMinutes(15));
  }

  @Test
  @DisplayName("Gives every token a unique id")
  void tokenIdsAreUnique() {
    JwtTokenService service = service(Clock.systemUTC());

    String first = decoder.decode(service.generateAccessToken(user())).getId();
    String second = decoder.decode(service.generateAccessToken(user())).getId();

    assertThat(first).isNotEqualTo(second);
  }

  @Test
  @DisplayName("Reports the configured lifetime in seconds")
  void reportsExpirationInSeconds() {
    assertThat(service(Clock.systemUTC()).accessTokenExpiration()).isEqualTo(900);
  }

  @Test
  @DisplayName("Rejects a token whose signature was tampered with")
  void rejectsTamperedToken() {
    String token = service(Clock.systemUTC()).generateAccessToken(user());
    String tampered = token.substring(0, token.length() - 4) + "AAAA";

    assertThatThrownBy(() -> decoder.decode(tampered)).isInstanceOf(JwtException.class);
  }

  @Test
  @DisplayName("Rejects an expired token")
  void rejectsExpiredToken() {
    Clock anHourAgo = Clock.fixed(Instant.now().minus(Duration.ofHours(1)), ZoneOffset.UTC);
    String token = service(anHourAgo).generateAccessToken(user());

    assertThatThrownBy(() -> decoder.decode(token)).isInstanceOf(JwtException.class);
  }

  @Test
  @DisplayName("Rejects a token signed by a different key")
  void rejectsTokenFromAnotherKey() throws Exception {
    RSAKey otherKey = keys.jwtSigningKey(properties);
    JwtTokenService other =
        new JwtTokenService(keys.jwtEncoder(otherKey), properties, otherKey, Clock.systemUTC());

    String token = other.generateAccessToken(user());

    assertThatThrownBy(() -> decoder.decode(token)).isInstanceOf(JwtException.class);
  }

  @Test
  @DisplayName("Rejects a token for another audience")
  void rejectsWrongAudience() {
    JwtTokenService otherAudience =
        new JwtTokenService(
            encoder, properties(ISSUER, "some-other-api"), signingKey, Clock.systemUTC());

    String token = otherAudience.generateAccessToken(user());

    assertThatThrownBy(() -> decoder.decode(token)).isInstanceOf(JwtException.class);
  }

  @Test
  @DisplayName("Rejects a token from another issuer")
  void rejectsWrongIssuer() {
    JwtTokenService otherIssuer =
        new JwtTokenService(
            encoder,
            properties("https://evil.example.com", AUDIENCE),
            signingKey,
            Clock.systemUTC());

    String token = otherIssuer.generateAccessToken(user());

    assertThatThrownBy(() -> decoder.decode(token)).isInstanceOf(JwtException.class);
  }

  @Test
  @DisplayName("Fails at startup when no key is configured and ephemeral keys are off")
  void failsWithoutKeyConfiguration() {
    JwtProperties noKeys =
        new JwtProperties(ISSUER, AUDIENCE, Duration.ofMinutes(15), null, null, false);

    assertThatThrownBy(() -> keys.jwtSigningKey(noKeys))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("No JWT signing key configured");
  }

  private JwtTokenService service(Clock clock) {
    return new JwtTokenService(encoder, properties, signingKey, clock);
  }

  private static AuthenticatedUser user() {
    return new AuthenticatedUser(
        USER_ID, "baldev@example.com", EnumSet.of(RoleName.CUSTOMER, RoleName.ADMIN));
  }

  private static JwtProperties properties(String issuer, String audience) {
    return new JwtProperties(issuer, audience, Duration.ofMinutes(15), null, null, true);
  }
}
