package com.novabank.auth.infrastructure.persistence.user.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.novabank.auth.application.exception.DuplicateEmailException;
import com.novabank.auth.domain.model.User;
import com.novabank.auth.domain.model.UserStatus;
import com.novabank.auth.domain.valueobject.EmailAddress;
import com.novabank.auth.domain.valueobject.PasswordHash;
import com.novabank.auth.support.TestcontainersConfiguration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Runs the adapter against real PostgreSQL with the real Flyway schema. */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({TestcontainersConfiguration.class, JpaUserRepositoryAdapter.class})
class JpaUserRepositoryAdapterIT {

  private static final PasswordHash HASH =
      PasswordHash.of("$2a$10$yhjkvDQLAQTS9ymYWnwjP.sL6CxMrvs9ZKgvjyLhDvOtU83fpK2KO");

  @Autowired private JpaUserRepositoryAdapter adapter;

  @Test
  @DisplayName("Saves a user and finds it again by email and id")
  void savesAndFinds() {
    User saved = adapter.save(User.register(EmailAddress.of("it-save@example.com"), HASH));

    assertThat(saved.version()).isZero();
    assertThat(adapter.findByEmail(EmailAddress.of("IT-SAVE@example.com"))).isPresent();
    assertThat(adapter.findById(saved.id()))
        .get()
        .satisfies(user -> {
          assertThat(user.email().value()).isEqualTo("it-save@example.com");
          assertThat(user.status()).isEqualTo(UserStatus.PENDING_VERIFICATION);
          assertThat(user.roles()).isNotEmpty();
        });
    assertThat(adapter.existsByEmail(EmailAddress.of("it-save@example.com"))).isTrue();
  }

  @Test
  @DisplayName("The database constraint turns a duplicate email into DuplicateEmailException")
  void duplicateEmailIsRejectedByTheDatabase() {
    adapter.save(User.register(EmailAddress.of("it-dup@example.com"), HASH));

    assertThatThrownBy(
            () -> adapter.save(User.register(EmailAddress.of("it-dup@example.com"), HASH)))
        .isInstanceOf(DuplicateEmailException.class);
  }

  @Test
  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  @DisplayName("A stale update fails instead of overwriting a newer one")
  void staleUpdateFails() {
    User saved = adapter.save(User.register(EmailAddress.of("it-lock@example.com"), HASH));

    User first = adapter.findById(saved.id()).orElseThrow();
    User second = adapter.findById(saved.id()).orElseThrow();

    first.activate();
    User updated = adapter.save(first);
    assertThat(updated.version()).isEqualTo(1L);

    second.lock();
    assertThatThrownBy(() -> adapter.save(second))
        .isInstanceOf(ObjectOptimisticLockingFailureException.class);
  }
}
