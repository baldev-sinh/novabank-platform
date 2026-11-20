package com.novabank.auth.application.security;

import java.time.Duration;
import java.util.Objects;

/**
 * @param idleTimeout a refresh token expires if not used for this long
 * @param absoluteLifetime a sign-in ends this long after the password was entered, however often it
 *     is refreshed (RFC 10017: rotation never extends the original expiry)
 * @param reuseGrace a token used this recently is refused without revoking its family: two tabs
 *     refreshing at the same moment are not an attack
 */
public record RefreshTokenSettings(
    Duration idleTimeout, Duration absoluteLifetime, Duration reuseGrace) {

  public RefreshTokenSettings {
    Objects.requireNonNull(idleTimeout, "idleTimeout cannot be null");
    Objects.requireNonNull(absoluteLifetime, "absoluteLifetime cannot be null");
    Objects.requireNonNull(reuseGrace, "reuseGrace cannot be null");
    if (idleTimeout.compareTo(absoluteLifetime) > 0) {
      throw new IllegalArgumentException("idleTimeout must not exceed absoluteLifetime");
    }
  }
}
