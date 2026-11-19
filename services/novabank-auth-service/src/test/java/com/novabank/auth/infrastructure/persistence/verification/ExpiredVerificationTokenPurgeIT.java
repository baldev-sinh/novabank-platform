package com.novabank.auth.infrastructure.persistence.verification;

import static org.assertj.core.api.Assertions.assertThat;

import com.novabank.auth.domain.model.User;
import com.novabank.auth.domain.valueobject.EmailAddress;
import com.novabank.auth.domain.valueobject.PasswordHash;
import com.novabank.auth.infrastructure.persistence.user.adapter.JpaUserRepositoryAdapter;
import com.novabank.auth.support.TestcontainersConfiguration;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** The purge against real PostgreSQL: only long-expired tokens go, in batches. */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({
  TestcontainersConfiguration.class,
  JpaUserRepositoryAdapter.class,
  JpaEmailVerificationTokenStore.class
})
// Each purge batch commits on its own, like in production.
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class ExpiredVerificationTokenPurgeIT {

  private static final PasswordHash HASH =
      PasswordHash.of("$2a$10$yhjkvDQLAQTS9ymYWnwjP.sL6CxMrvs9ZKgvjyLhDvOtU83fpK2KO");
  private static final Instant NOW = Instant.parse("2026-11-19T10:00:00Z");

  @Autowired private JpaUserRepositoryAdapter users;
  @Autowired private JpaEmailVerificationTokenStore store;
  @Autowired private JdbcTemplate jdbcTemplate;

  @Test
  @DisplayName("Tokens expired longer than the retention are deleted; recent and valid ones stay")
  void purgesOnlyLongExpiredTokens() {
    UUID userId = newUser();
    String longExpired = token(userId, NOW.minus(Duration.ofDays(9)));
    String longExpiredUsed = token(userId, NOW.minus(Duration.ofDays(8)));
    jdbcTemplate.update(
        "UPDATE email_verification_tokens SET used_at = expires_at WHERE token_hash = ?",
        longExpiredUsed);
    String recentlyExpired = token(userId, NOW.minus(Duration.ofDays(2)));
    String valid = token(userId, NOW.plus(Duration.ofHours(20)));

    purger(Duration.ofDays(7)).purge();

    assertThat(remaining(longExpired, longExpiredUsed, recentlyExpired, valid))
        .containsExactlyInAnyOrder(recentlyExpired, valid);
  }

  @Test
  @DisplayName("A backlog larger than one batch is deleted completely in one run")
  void purgesBacklogInBatches() {
    UUID userId = newUser();
    Instant expired = NOW.minus(Duration.ofDays(30));
    jdbcTemplate.update(
        """
        INSERT INTO email_verification_tokens (id, user_id, token_hash, created_at, expires_at)
        SELECT gen_random_uuid(), ?, md5(random()::text) || md5(random()::text), ?, ?
        FROM generate_series(1, ?)
        """,
        userId,
        Timestamp.from(expired.minus(Duration.ofDays(1))),
        Timestamp.from(expired),
        ExpiredVerificationTokenPurger.BATCH_SIZE * 2 + 5);

    purger(Duration.ofDays(7)).purge();

    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT count(*) FROM email_verification_tokens WHERE user_id = ?",
                Integer.class,
                userId))
        .isZero();
  }

  private ExpiredVerificationTokenPurger purger(Duration retention) {
    return new ExpiredVerificationTokenPurger(store, Clock.fixed(NOW, ZoneOffset.UTC), retention);
  }

  private String token(UUID userId, Instant expiresAt) {
    String hash = UUID.randomUUID().toString().replace("-", "") + "0".repeat(32);
    store.save(userId, hash, expiresAt.minus(Duration.ofDays(1)), expiresAt);
    return hash;
  }

  private List<String> remaining(String... hashes) {
    return jdbcTemplate.queryForList(
        "SELECT token_hash FROM email_verification_tokens WHERE token_hash = ANY (?)",
        String.class,
        (Object) hashes);
  }

  private UUID newUser() {
    String email = "purge-" + UUID.randomUUID() + "@example.com";
    return users.save(User.register(EmailAddress.of(email), HASH)).id().value();
  }
}
