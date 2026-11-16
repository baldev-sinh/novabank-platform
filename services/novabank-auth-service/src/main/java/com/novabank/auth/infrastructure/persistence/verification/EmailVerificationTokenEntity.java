package com.novabank.auth.infrastructure.persistence.verification;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** One row of {@code email_verification_tokens}; see V4 migration. */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "email_verification_tokens")
public class EmailVerificationTokenEntity {

  @Id private UUID id;

  @Column(name = "user_id", nullable = false, updatable = false)
  private UUID userId;

  @Column(name = "token_hash", nullable = false, updatable = false, length = 64)
  private String tokenHash;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "expires_at", nullable = false, updatable = false)
  private Instant expiresAt;

  @Column(name = "used_at")
  private Instant usedAt;

  static EmailVerificationTokenEntity of(
      UUID userId, String tokenHash, Instant createdAt, Instant expiresAt) {
    EmailVerificationTokenEntity entity = new EmailVerificationTokenEntity();
    entity.id = UUID.randomUUID();
    entity.userId = userId;
    entity.tokenHash = tokenHash;
    entity.createdAt = createdAt;
    entity.expiresAt = expiresAt;
    return entity;
  }
}
