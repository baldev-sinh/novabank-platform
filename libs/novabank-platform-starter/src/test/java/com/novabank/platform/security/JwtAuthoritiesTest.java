package com.novabank.platform.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

class JwtAuthoritiesTest {

  /** Spring Security 7 records how the request authenticated (here: a bearer token). */
  private static final String FACTOR_BEARER = "FACTOR_BEARER";

  @Test
  @DisplayName("User tokens: roles become ROLE_*")
  void userTokenRoles() {
    Jwt jwt = jwt("roles", List.of("CUSTOMER", "OPERATIONS"));

    assertThat(authorities(jwt))
        .containsExactlyInAnyOrder("ROLE_CUSTOMER", "ROLE_OPERATIONS", FACTOR_BEARER);
  }

  @Test
  @DisplayName("Service tokens: scopes become SCOPE_*, and there are no roles")
  void serviceTokenScopes() {
    Jwt jwt = jwt("scope", "accounts:read");

    assertThat(authorities(jwt)).containsExactlyInAnyOrder("SCOPE_accounts:read", FACTOR_BEARER);
  }

  private static List<String> authorities(Jwt jwt) {
    return ResourceServerSecurity.jwtAuthenticationConverter()
        .convert(jwt)
        .getAuthorities()
        .stream()
        .map(GrantedAuthority::getAuthority)
        .toList();
  }

  private static Jwt jwt(String claim, Object value) {
    return Jwt.withTokenValue("token")
        .header("alg", "RS256")
        .subject("subject")
        .claim(claim, value)
        .issuedAt(Instant.now())
        .expiresAt(Instant.now().plusSeconds(60))
        .build();
  }
}
