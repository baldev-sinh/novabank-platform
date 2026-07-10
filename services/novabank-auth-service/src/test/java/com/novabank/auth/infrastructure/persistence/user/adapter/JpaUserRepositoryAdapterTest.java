package com.novabank.auth.infrastructure.persistence.user.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.novabank.auth.domain.model.RoleName;
import com.novabank.auth.domain.model.User;
import com.novabank.auth.domain.model.UserStatus;
import com.novabank.auth.domain.valueobject.EmailAddress;
import com.novabank.auth.domain.valueobject.PasswordHash;
import com.novabank.auth.domain.valueobject.identifier.UserId;
import com.novabank.auth.infrastructure.persistence.user.entity.UserEntity;
import com.novabank.auth.infrastructure.persistence.user.repository.SpringDataUserRepository;
import java.time.Instant;
import java.util.EnumSet;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class JpaUserRepositoryAdapterTest {

    @Mock
    private SpringDataUserRepository repository;

    private JpaUserRepositoryAdapter adapter;

    private static final UserId USER_ID = UserId.random();
    private static final EmailAddress EMAIL =
        EmailAddress.of("baldev@example.com");
    private static final PasswordHash PASSWORD =
        PasswordHash.of("$2a$10$hash");
    private static final Instant CREATED_AT = Instant.now();
    private static final Instant UPDATED_AT = Instant.now();

    @BeforeEach
    void setUp() {
        adapter = new JpaUserRepositoryAdapter(repository);
    }

    @Test
    @DisplayName("Should save user and return restored domain user")
    void shouldSaveUser() {

        User user = createUser();
        UserEntity entity = createUserEntity();

        when(repository.save(any(UserEntity.class)))
            .thenReturn(entity);

        User result = adapter.save(user);

        verify(repository, times(1))
            .save(any(UserEntity.class));

        verifyNoMoreInteractions(repository);

        assertThat(result.id()).isEqualTo(user.id());
        assertThat(result.email()).isEqualTo(user.email());
        assertThat(result.passwordHash()).isEqualTo(user.passwordHash());
        assertThat(result.status()).isEqualTo(user.status());
        assertThat(result.roles())
            .containsExactlyInAnyOrderElementsOf(user.roles());
        assertThat(result.createdAt()).isEqualTo(user.createdAt());
        assertThat(result.updatedAt()).isEqualTo(user.updatedAt());
    }

    @Test
    @DisplayName("Should reject null user")
    void shouldRejectNullUser() {

        assertThatThrownBy(() -> adapter.save(null))
            .isInstanceOf(NullPointerException.class)
            .hasMessage("User cannot be null");
    }

    @Test
    @DisplayName("Should return user when id exists")
    void shouldReturnUserWhenIdExists() {

        when(repository.findById(USER_ID.value()))
            .thenReturn(Optional.of(createUserEntity()));

        Optional<User> result = adapter.findById(USER_ID);

        assertThat(result).isPresent();

        assertThat(result.get().id()).isEqualTo(USER_ID);
        assertThat(result.get().email()).isEqualTo(EMAIL);
        assertThat(result.get().status()).isEqualTo(UserStatus.ACTIVE);

        verify(repository, times(1))
            .findById(USER_ID.value());
        verifyNoMoreInteractions(repository);
    }

    @Test
    @DisplayName("Should return empty when id does not exist")
    void shouldReturnEmptyWhenIdDoesNotExist() {

        when(repository.findById(USER_ID.value()))
            .thenReturn(Optional.empty());

        Optional<User> result = adapter.findById(USER_ID);

        assertThat(result).isEmpty();

        verify(repository, times(1))
            .findById(USER_ID.value());
        verifyNoMoreInteractions(repository);
    }

    @Test
    @DisplayName("Should reject null user id")
    void shouldRejectNullUserId() {

        assertThatThrownBy(() -> adapter.findById(null))
            .isInstanceOf(NullPointerException.class)
            .hasMessage("UserId cannot be null");
    }

    @Test
    @DisplayName("Should return user when email exists")
    void shouldReturnUserWhenEmailExists() {

        when(repository.findByEmail(EMAIL.value()))
            .thenReturn(Optional.of(createUserEntity()));

        Optional<User> result = adapter.findByEmail(EMAIL);

        assertThat(result).isPresent();
        assertThat(result.get().email()).isEqualTo(EMAIL);

        verify(repository, times(1))
            .findByEmail(EMAIL.value());
        verifyNoMoreInteractions(repository);
    }

    @Test
    @DisplayName("Should return empty when email does not exist")
    void shouldReturnEmptyWhenEmailDoesNotExist() {

        when(repository.findByEmail(EMAIL.value()))
            .thenReturn(Optional.empty());

        Optional<User> result = adapter.findByEmail(EMAIL);

        assertThat(result).isEmpty();

        verify(repository, times(1))
            .findByEmail(EMAIL.value());
        verifyNoMoreInteractions(repository);
    }

    @Test
    @DisplayName("Should reject null email")
    void shouldRejectNullEmail() {

        assertThatThrownBy(() -> adapter.findByEmail(null))
            .isInstanceOf(NullPointerException.class)
            .hasMessage("Email cannot be null");

    }

    @Test
    @DisplayName("Should return true when email exists")
    void shouldReturnTrueWhenEmailExists() {

        when(repository.existsByEmail(EMAIL.value()))
            .thenReturn(true);

        boolean result = adapter.existsByEmail(EMAIL);

        assertThat(result).isTrue();

        verify(repository, times(1))
            .existsByEmail(EMAIL.value());
        verifyNoMoreInteractions(repository);
    }

    @Test
    @DisplayName("Should return false when email does not exist")
    void shouldReturnFalseWhenEmailDoesNotExist() {

        when(repository.existsByEmail(EMAIL.value()))
            .thenReturn(false);

        boolean result = adapter.existsByEmail(EMAIL);

        assertThat(result).isFalse();

        verify(repository, times(1))
            .existsByEmail(EMAIL.value());
        verifyNoMoreInteractions(repository);
    }

    @Test
    @DisplayName("Should reject null email for existsByEmail")
    void shouldRejectNullEmailForExistsByEmail() {

        assertThatThrownBy(() -> adapter.existsByEmail(null))
            .isInstanceOf(NullPointerException.class)
            .hasMessage("Email cannot be null");
        verifyNoMoreInteractions(repository);
    }

    private User createUser() {
        return User.restore(
            USER_ID,
            EMAIL,
            PASSWORD,
            UserStatus.ACTIVE,
            EnumSet.of(RoleName.CUSTOMER),
            CREATED_AT,
            UPDATED_AT
        );
    }

    private UserEntity createUserEntity() {
        return UserEntity.from(
            USER_ID.value(),
            EMAIL.value(),
            PASSWORD.value(),
            UserStatus.ACTIVE,
            EnumSet.of(RoleName.CUSTOMER),
            CREATED_AT,
            UPDATED_AT
        );
    }
}
