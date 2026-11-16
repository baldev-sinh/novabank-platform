package com.novabank.auth.application.port.persistence;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/** Stores hashes of email verification tokens, never the tokens themselves. */
public interface EmailVerificationTokenStore {

  void save(UUID userId, String tokenHash, Instant createdAt, Instant expiresAt);

  /** Removes the user's unused tokens, so only the newest link works. */
  void deleteUnusedFor(UUID userId);

  /** When the user's newest token was created, if they have one. */
  Optional<Instant> latestCreatedAt(UUID userId);

  /**
   * Marks the token used if it exists, is unused and hasn't expired. Atomic: two concurrent calls
   * with the same token can't both succeed.
   *
   * @return the token's user, or empty if the token can't be used
   */
  Optional<UUID> consume(String tokenHash, Instant now);
}
