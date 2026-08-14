package com.novabank.auth.infrastructure.security;

import com.novabank.auth.application.port.security.CurrentUserProvider;
import com.novabank.auth.application.security.JwtUser;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Component
public class SpringSecurityCurrentUserProvider implements CurrentUserProvider {

  @Override
  public JwtUser getCurrentUser() {

    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

    if (authentication == null || !authentication.isAuthenticated()) {
      throw new IllegalStateException("No authenticated user found");
    }

    Object principal = authentication.getPrincipal();

    if (!(principal instanceof JwtUser jwtUser)) {
      throw new IllegalStateException("Authenticated principal is not a JwtUser");
    }

    return jwtUser;
  }
}
