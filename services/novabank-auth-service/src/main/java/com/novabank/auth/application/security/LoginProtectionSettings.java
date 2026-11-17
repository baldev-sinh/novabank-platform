package com.novabank.auth.application.security;

import java.time.Duration;
import java.util.Objects;

/**
 * Account lockout policy (Decision S). The lock is temporary, so an attacker who knows an email can
 * delay that user but never lock them out for good.
 *
 * @param maxFailedAttempts consecutive failures that lock the account
 * @param failureWindow failures older than this are forgotten
 * @param lockoutDuration how long a lock lasts
 */
public record LoginProtectionSettings(
    int maxFailedAttempts, Duration failureWindow, Duration lockoutDuration) {

  public LoginProtectionSettings {
    if (maxFailedAttempts < 2) {
      throw new IllegalArgumentException("maxFailedAttempts must be at least 2");
    }
    Objects.requireNonNull(failureWindow, "failureWindow cannot be null");
    Objects.requireNonNull(lockoutDuration, "lockoutDuration cannot be null");
  }
}
