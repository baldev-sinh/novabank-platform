package com.novabank.auth.infrastructure.persistence.verification;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface SpringDataEmailVerificationTokenRepository
    extends JpaRepository<EmailVerificationTokenEntity, UUID> {

  Optional<EmailVerificationTokenEntity> findByTokenHash(String tokenHash);

  Optional<EmailVerificationTokenEntity> findFirstByUserIdOrderByCreatedAtDesc(UUID userId);

  @Modifying
  @Query("DELETE FROM EmailVerificationTokenEntity t WHERE t.userId = :userId AND t.usedAt IS NULL")
  void deleteUnusedByUserId(@Param("userId") UUID userId);

  /**
   * Conditional update: the database row lock makes it atomic, so of two concurrent requests with
   * the same token only one gets 1 back.
   */
  @Modifying
  @Query(
      """
      UPDATE EmailVerificationTokenEntity t SET t.usedAt = :now
      WHERE t.id = :id AND t.usedAt IS NULL AND t.expiresAt > :now
      """)
  int markUsed(@Param("id") UUID id, @Param("now") Instant now);
}
