package com.novabank.auth.infrastructure.persistence.loginattempt;

import static org.assertj.core.api.Assertions.assertThat;

import com.novabank.auth.application.port.persistence.LoginAttemptStore.LoginAttempts;
import com.novabank.auth.domain.model.User;
import com.novabank.auth.domain.valueobject.EmailAddress;
import com.novabank.auth.domain.valueobject.PasswordHash;
import com.novabank.auth.infrastructure.persistence.user.adapter.JpaUserRepositoryAdapter;
import com.novabank.auth.support.TestcontainersConfiguration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** The counter against real PostgreSQL: window, lock, reset, and concurrent failures. */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({
  TestcontainersConfiguration.class,
  JpaUserRepositoryAdapter.class,
  JdbcLoginAttemptStore.class
})
// The store writes in its own transactions, so the user row must be committed first.
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class JdbcLoginAttemptStoreIT {

  private static final PasswordHash HASH =
      PasswordHash.of("$2a$10$yhjkvDQLAQTS9ymYWnwjP.sL6CxMrvs9ZKgvjyLhDvOtU83fpK2KO");
  private static final Instant NOW = Instant.parse("2026-11-17T10:00:00Z");
  private static final Instant WINDOW_START = NOW.minusSeconds(900);
  private static final Instant LOCK_END = NOW.plusSeconds(900);

  @Autowired private JpaUserRepositoryAdapter users;
  @Autowired private JdbcLoginAttemptStore store;

  @Test
  @DisplayName("Failures count up; the 5th locks the account and restarts the count")
  void fifthFailureLocks() {
    UUID userId = newUser();

    for (int i = 1; i <= 4; i++) {
      assertThat(store.recordFailure(userId, NOW, WINDOW_START, 5, LOCK_END))
          .isEqualTo(new LoginAttempts(i, null));
    }
    LoginAttempts fifth = store.recordFailure(userId, NOW, WINDOW_START, 5, LOCK_END);

    assertThat(fifth.failedCount()).isZero();
    assertThat(fifth.isLockedAt(NOW)).isTrue();
    assertThat(store.find(userId)).contains(fifth);
  }

  @Test
  @DisplayName("Failures older than the window are forgotten")
  void oldFailuresAreForgotten() {
    UUID userId = newUser();
    Instant longAgo = NOW.minusSeconds(3600);
    for (int i = 0; i < 4; i++) {
      store.recordFailure(userId, longAgo, longAgo.minusSeconds(900), 5, longAgo.plusSeconds(900));
    }

    assertThat(store.recordFailure(userId, NOW, WINDOW_START, 5, LOCK_END).failedCount())
        .isEqualTo(1);
  }

  @Test
  @DisplayName("A successful sign-in clears the record")
  void resetClears() {
    UUID userId = newUser();
    store.recordFailure(userId, NOW, WINDOW_START, 5, LOCK_END);

    store.reset(userId);

    assertThat(store.find(userId)).isEmpty();
  }

  @Test
  @DisplayName("20 concurrent failures lose no update: exactly 4 of them lock the account")
  void concurrentFailuresAreAtomic() throws Exception {
    UUID userId = newUser();
    Callable<LoginAttempts> failure =
        () -> store.recordFailure(userId, NOW, WINDOW_START, 5, LOCK_END);

    List<LoginAttempts> results = new ArrayList<>();
    try (ExecutorService pool = Executors.newFixedThreadPool(8)) {
      List<Future<LoginAttempts>> futures = new ArrayList<>();
      for (int i = 0; i < 20; i++) {
        futures.add(pool.submit(failure));
      }
      for (Future<LoginAttempts> future : futures) {
        results.add(future.get());
      }
    }

    assertThat(results).filteredOn(result -> result.failedCount() == 0).hasSize(4);
    assertThat(results).extracting(LoginAttempts::failedCount).containsOnly(0, 1, 2, 3, 4);
    assertThat(store.find(userId)).get().extracting(LoginAttempts::failedCount).isEqualTo(0);
  }

  private UUID newUser() {
    String email = "lockout-" + UUID.randomUUID() + "@example.com";
    return users.save(User.register(EmailAddress.of(email), HASH)).id().value();
  }
}
