package com.novabank.auth.application.service;

import com.novabank.auth.application.port.persistence.RefreshTokenStore;
import com.novabank.auth.application.port.persistence.RefreshTokenStore.NewRefreshToken;
import com.novabank.auth.application.security.RefreshTokenSettings;
import com.novabank.auth.application.security.RefreshTokenValue;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Creates refresh tokens: a new family at sign-in, the next one in the family on refresh. */
@Component
@RequiredArgsConstructor
public class RefreshTokenIssuer {

  private final RefreshTokenStore tokens;
  private final RefreshTokenSettings settings;
  private final Clock clock;

  /** A new sign-in: the absolute lifetime starts now. */
  public IssuedRefreshToken startFamily(UUID userId) {
    Instant now = clock.instant();
    return issue(userId, UUID.randomUUID(), now, now.plus(settings.absoluteLifetime()));
  }

  /** The next token of an existing sign-in; never extends the family's absolute limit. */
  public IssuedRefreshToken rotate(UUID userId, UUID familyId, Instant familyExpiresAt) {
    return issue(userId, familyId, clock.instant(), familyExpiresAt);
  }

  private IssuedRefreshToken issue(
      UUID userId, UUID familyId, Instant now, Instant familyExpiresAt) {
    RefreshTokenValue token = RefreshTokenValue.generate();
    Instant idleExpiry = now.plus(settings.idleTimeout());
    Instant expiresAt = idleExpiry.isBefore(familyExpiresAt) ? idleExpiry : familyExpiresAt;
    tokens.save(
        new NewRefreshToken(
            UUID.randomUUID(), familyId, userId, token.hash(), now, expiresAt, familyExpiresAt));
    return new IssuedRefreshToken(token.value(), familyExpiresAt);
  }

  /**
   * @param cookieExpiresAt when the browser should drop the cookie: the end of the sign-in. The
   *     shorter idle timeout is enforced by the server.
   */
  public record IssuedRefreshToken(String value, Instant cookieExpiresAt) {

    @Override
    public String toString() {
      return "IssuedRefreshToken[****, cookieExpiresAt=" + cookieExpiresAt + "]";
    }
  }
}
