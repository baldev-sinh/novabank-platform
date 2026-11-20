package com.novabank.auth.presentation.rest.authentication;

import com.novabank.auth.application.service.RefreshTokenIssuer.IssuedRefreshToken;
import com.novabank.auth.infrastructure.configuration.RefreshTokenConfiguration.RefreshTokenProperties;
import java.time.Clock;
import java.time.Duration;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

/**
 * The refresh token cookie (Decision AE): HttpOnly so scripts can't read it, Secure,
 * SameSite=Strict so the browser never sends it on a cross-site request, and scoped to the auth
 * endpoints that use it. No Domain attribute: it stays on the auth service's host.
 */
@Component
class RefreshCookie {

  static final String PATH = "/api/v1/auth";

  private final RefreshTokenProperties properties;
  private final Clock clock;

  RefreshCookie(RefreshTokenProperties properties, Clock clock) {
    this.properties = properties;
    this.clock = clock;
  }

  String name() {
    return properties.cookieName();
  }

  ResponseCookie of(IssuedRefreshToken token) {
    Duration maxAge = Duration.between(clock.instant(), token.cookieExpiresAt());
    return base(token.value()).maxAge(maxAge.isNegative() ? Duration.ZERO : maxAge).build();
  }

  /** Tells the browser to drop the cookie. */
  ResponseCookie cleared() {
    return base("").maxAge(Duration.ZERO).build();
  }

  private ResponseCookie.ResponseCookieBuilder base(String value) {
    return ResponseCookie.from(properties.cookieName(), value)
        .httpOnly(true)
        .secure(properties.cookieSecure())
        .sameSite("Strict")
        .path(PATH);
  }
}
