package com.novabank.auth.infrastructure.security;

import com.novabank.auth.application.port.security.CurrentUserProvider;
import com.novabank.auth.application.security.AuthenticatedUser;
import com.novabank.auth.domain.model.RoleName;
import java.util.EnumSet;
import java.util.List;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

/** Reads the current user from the validated JWT that the resource server put in the context. */
@Component
public class SpringSecurityCurrentUserProvider implements CurrentUserProvider {

  @Override
  public AuthenticatedUser getCurrentUser() {
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    if (authentication == null || !authentication.isAuthenticated()) {
      throw new IllegalStateException("No authenticated user found");
    }
    if (!(authentication.getPrincipal() instanceof Jwt jwt)) {
      throw new IllegalStateException("Authenticated principal is not a JWT");
    }
    return new AuthenticatedUser(
        UUID.fromString(jwt.getSubject()), jwt.getClaimAsString(JwtClaims.EMAIL), roles(jwt));
  }

  private static EnumSet<RoleName> roles(Jwt jwt) {
    List<String> names = jwt.getClaimAsStringList(JwtClaims.ROLES);
    EnumSet<RoleName> roles = EnumSet.noneOf(RoleName.class);
    if (names != null) {
      names.forEach(name -> roles.add(RoleName.valueOf(name)));
    }
    return roles;
  }
}
