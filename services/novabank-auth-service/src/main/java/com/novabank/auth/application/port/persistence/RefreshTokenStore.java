package com.novabank.auth.application.port.persistence;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/** Refresh tokens, by hash only (7.1). */
public interface RefreshTokenStore {

  void save(NewRefreshToken token);

  /**
   * Marks the token used if it is unused, not revoked and neither it nor its family has expired.
   * Atomic: of two concurrent calls with the same token, only one succeeds.
   */
  Optional<Consumed> consume(String tokenHash, Instant now);

  /** The token in whatever state it is, to tell a reused token from an unknown one. */
  Optional<Stored> find(String tokenHash);

  /**
   * Revokes every token of the sign-in. Commits on its own, so the revocation stands even though
   * the request that detected the reuse fails.
   */
  void revokeFamily(UUID familyId, Instant now);

  /** Deletes up to {@code limit} tokens whose sign-in ended before {@code cutoff}. */
  int deleteExpiredBefore(Instant cutoff, int limit);

  record NewRefreshToken(
      UUID id,
      UUID familyId,
      UUID userId,
      String tokenHash,
      Instant createdAt,
      Instant expiresAt,
      Instant familyExpiresAt) {}

  record Consumed(UUID userId, UUID familyId, Instant familyExpiresAt) {}

  record Stored(UUID userId, UUID familyId, Instant usedAt, Instant revokedAt) {}
}
