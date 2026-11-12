package com.novabank.auth.infrastructure.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.novabank.auth.application.security.AuthenticatedUser;
import com.novabank.auth.domain.model.RoleName;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

class SpringSecurityCurrentUserProviderTest {

  private static final UUID USER_ID = UUID.fromString("7f1c2d1e-8c55-4c4f-9a7e-2b5c8d9e0f11");

  private final SpringSecurityCurrentUserProvider provider =
      new SpringSecurityCurrentUserProvider();

  @AfterEach
  void clearContext() {
    SecurityContextHolder.clearContext();
  }

  @Test
  @DisplayName("Builds the current user from the validated JWT")
  void returnsUserFromJwt() {
    authenticate(jwt(List.of("CUSTOMER", "ADMIN")));

    AuthenticatedUser user = provider.getCurrentUser();

    assertThat(user.userId()).isEqualTo(USER_ID);
    assertThat(user.email()).isEqualTo("baldev@example.com");
    assertThat(user.roles()).containsExactlyInAnyOrder(RoleName.CUSTOMER, RoleName.ADMIN);
  }

  @Test
  @DisplayName("Returns no roles when the roles claim is missing")
  void handlesMissingRolesClaim() {
    authenticate(jwt(null));

    assertThat(provider.getCurrentUser().roles()).isEmpty();
  }

  @Test
  @DisplayName("Fails when nobody is authenticated")
  void failsWithoutAuthentication() {
    assertThatThrownBy(provider::getCurrentUser)
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("No authenticated user found");
  }

  @Test
  @DisplayName("Fails when the principal is not a JWT")
  void failsForNonJwtPrincipal() {
    TestingAuthenticationToken other = new TestingAuthenticationToken("someone", "n/a");
    other.setAuthenticated(true);
    SecurityContextHolder.getContext().setAuthentication(other);

    assertThatThrownBy(provider::getCurrentUser)
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("Authenticated principal is not a JWT");
  }

  private static void authenticate(Jwt jwt) {
    SecurityContextHolder.getContext()
        .setAuthentication(new JwtAuthenticationToken(jwt, List.of()));
  }

  private static Jwt jwt(List<String> roles) {
    Jwt.Builder builder =
        Jwt.withTokenValue("token")
            .header("alg", "RS256")
            .subject(USER_ID.toString())
            .claim(JwtClaims.EMAIL, "baldev@example.com")
            .issuedAt(Instant.now())
            .expiresAt(Instant.now().plusSeconds(60));
    if (roles != null) {
      builder.claim(JwtClaims.ROLES, roles);
    }
    return builder.build();
  }
}
