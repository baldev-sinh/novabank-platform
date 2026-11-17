package com.novabank.auth.application.port.persistence;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Failed sign-ins per account, kept outside the {@code User} aggregate: a brute-force attack sends
 * many requests at once, and atomic counter updates never conflict the way versioned aggregate
 * updates would.
 */
public interface LoginAttemptStore {

  /** Current state, or empty if the account has no recent failures. */
  Optional<LoginAttempts> find(UUID userId);

  /**
   * Counts one failure atomically. Failures older than {@code windowStart} no longer count; the
   * {@code maxFailures}-th failure locks the account until {@code lockedUntil} and restarts the
   * count.
   *
   * @return the state after this failure
   */
  LoginAttempts recordFailure(
      UUID userId, Instant now, Instant windowStart, int maxFailures, Instant lockedUntil);

  /** After a successful sign-in. */
  void reset(UUID userId);

  /**
   * @param failedCount failures in the current window
   * @param lockedUntil end of the current or last lock, if any
   */
  record LoginAttempts(int failedCount, Instant lockedUntil) {

    public boolean isLockedAt(Instant now) {
      return lockedUntil != null && lockedUntil.isAfter(now);
    }
  }
}
