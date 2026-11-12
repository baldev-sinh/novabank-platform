package com.novabank.customer.infrastructure.configuration;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;

class SecurityConfigurationTest {

  private final OAuth2TokenValidator<Jwt> validator =
      SecurityConfiguration.tokenValidator(
          new JwtProperties("http://unused", "http://auth.test", "novabank-api"));

  @Test
  @DisplayName("Accepts a token with our issuer and audience")
  void acceptsValidToken() {
    assertThat(validator.validate(token("http://auth.test", "novabank-api")).hasErrors()).isFalse();
  }

  @Test
  @DisplayName("Rejects a token from another issuer")
  void rejectsWrongIssuer() {
    assertThat(validator.validate(token("https://evil.example.com", "novabank-api")).hasErrors())
        .isTrue();
  }

  @Test
  @DisplayName("Rejects a token meant for another audience")
  void rejectsWrongAudience() {
    assertThat(validator.validate(token("http://auth.test", "another-api")).hasErrors()).isTrue();
  }

  @Test
  @DisplayName("Rejects an expired token")
  void rejectsExpiredToken() {
    Jwt expired =
        Jwt.withTokenValue("t")
            .header("alg", "RS256")
            .issuer("http://auth.test")
            .audience(List.of("novabank-api"))
            .issuedAt(Instant.now().minusSeconds(3600))
            .expiresAt(Instant.now().minusSeconds(1800))
            .build();

    assertThat(validator.validate(expired).hasErrors()).isTrue();
  }

  private static Jwt token(String issuer, String audience) {
    return Jwt.withTokenValue("t")
        .header("alg", "RS256")
        .issuer(issuer)
        .audience(List.of(audience))
        .subject("7f1c2d1e-8c55-4c4f-9a7e-2b5c8d9e0f11")
        .issuedAt(Instant.now())
        .expiresAt(Instant.now().plusSeconds(900))
        .build();
  }
}
