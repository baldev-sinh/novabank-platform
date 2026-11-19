package com.novabank.auth.infrastructure.persistence.verification;

import com.novabank.auth.application.port.persistence.EmailVerificationTokenStore;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class JpaEmailVerificationTokenStore implements EmailVerificationTokenStore {

  private final SpringDataEmailVerificationTokenRepository repository;

  @Override
  public void save(UUID userId, String tokenHash, Instant createdAt, Instant expiresAt) {
    repository.save(EmailVerificationTokenEntity.of(userId, tokenHash, createdAt, expiresAt));
  }

  @Override
  public void deleteUnusedFor(UUID userId) {
    repository.deleteUnusedByUserId(userId);
  }

  @Override
  public Optional<Instant> latestCreatedAt(UUID userId) {
    return repository
        .findFirstByUserIdOrderByCreatedAtDesc(userId)
        .map(EmailVerificationTokenEntity::getCreatedAt);
  }

  @Override
  public Optional<UUID> consume(String tokenHash, Instant now) {
    return repository
        .findByTokenHash(tokenHash)
        .filter(token -> repository.markUsed(token.getId(), now) == 1)
        .map(EmailVerificationTokenEntity::getUserId);
  }

  @Override
  public int deleteExpiredBefore(Instant cutoff, int limit) {
    return repository.deleteExpiredBefore(cutoff, limit);
  }
}
