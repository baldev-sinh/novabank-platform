package com.novabank.auth.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.novabank.auth.application.command.RegisterUserCommand;
import com.novabank.auth.application.exception.DuplicateEmailException;
import com.novabank.auth.application.response.RegisterUserResponse;
import com.novabank.auth.domain.model.User;
import com.novabank.auth.domain.model.UserStatus;
import com.novabank.auth.domain.repository.UserRepository;
import com.novabank.auth.domain.valueobject.EmailAddress;
import com.novabank.auth.domain.valueobject.PasswordHash;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

class RegisterUserServiceTest {

    @Mock
    private UserRepository repository;
    private RegisterUserService service;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        service = new RegisterUserService(repository);
    }

    @Test
    @DisplayName("Should register user successfully")
    void shouldRegisterUserSuccessfully() {

        RegisterUserCommand command =
            new RegisterUserCommand(
                "baldev@example.com",
                "$2a$10$abcdefghijklmnopqrstuv"
            );

        User user = User.register(
            EmailAddress.of(command.email()),
            PasswordHash.of(command.password())
        );

        when(repository.existsByEmail(any(EmailAddress.class)))
            .thenReturn(false);

        when(repository.save(any(User.class)))
            .thenReturn(user);

        RegisterUserResponse response = service.register(command);

        assertThat(response).isNotNull();
        assertThat(response.userId()).isEqualTo(user.id().value());
        assertThat(response.email()).isEqualTo(user.email().value());
        assertThat(response.status()).isEqualTo(UserStatus.PENDING_VERIFICATION.toString());

        verify(repository).existsByEmail(any(EmailAddress.class));
        verify(repository).save(any(User.class));
    }

    @Test
    @DisplayName("Should reject null command")
    void shouldRejectNullCommand() {

        assertThatThrownBy(() -> service.register(null))
            .isInstanceOf(NullPointerException.class)
            .hasMessage("command cannot be null");
    }

    @Test
    @DisplayName("Should reject duplicate email")
    void shouldRejectDuplicateEmail() {

        RegisterUserCommand command =
            new RegisterUserCommand(
                "baldev@example.com",
                "$2a$10$abcdefghijklmnopqrstuv"
            );

        when(repository.existsByEmail(any(EmailAddress.class)))
            .thenReturn(true);

        assertThatThrownBy(() -> service.register(command))
            .isInstanceOf(DuplicateEmailException.class)
            .hasMessage("Email already registered: baldev@example.com");

        verify(repository, never()).save(any(User.class));
    }

    @Test
    @DisplayName("Should persist newly created user")
    void shouldPersistNewUser() {

        RegisterUserCommand command =
            new RegisterUserCommand(
                "baldev@example.com",
                "$2a$10$abcdefghijklmnopqrstuv"
            );

        User user = User.register(
            EmailAddress.of(command.email()),
            PasswordHash.of(command.password())
        );

        when(repository.existsByEmail(any()))
            .thenReturn(false);

        when(repository.save(any()))
            .thenReturn(user);

        service.register(command);

        verify(repository).save(any(User.class));
    }

    @Test
    @DisplayName("Should check email uniqueness before saving user")
    void shouldCheckEmailUniquenessBeforeSavingUser() {

        RegisterUserCommand command =
            new RegisterUserCommand(
                "baldev@example.com",
                "$2a$10$abcdefghijklmnopqrstuv"
            );

        User user = User.register(
            EmailAddress.of(command.email()),
            PasswordHash.of(command.password())
        );

        when(repository.existsByEmail(any()))
            .thenReturn(false);

        when(repository.save(any()))
            .thenReturn(user);

        service.register(command);

        InOrder inOrder = inOrder(repository);

        inOrder.verify(repository).existsByEmail(any());
        inOrder.verify(repository).save(any());
    }

    @Test
    @DisplayName("Should normalize email before checking uniqueness")
    void shouldNormalizeEmailBeforeCheckingDuplicate() {

        RegisterUserCommand command =
            new RegisterUserCommand(
                "  BALDEV@EXAMPLE.COM ",
                "$2a$10$abcdefghijklmnopqrstuv"
            );

        User user = User.register(
            EmailAddress.of(command.email()),
            PasswordHash.of(command.password())
        );

        when(repository.existsByEmail(any()))
            .thenReturn(false);

        when(repository.save(any()))
            .thenReturn(user);

        RegisterUserResponse response = service.register(command);

        assertThat(response.email())
            .isEqualTo("baldev@example.com");
    }

    @Test
    @DisplayName("Should propagate repository failure")
    void shouldPropagateRepositoryFailure() {

        RegisterUserCommand command =
            new RegisterUserCommand(
                "baldev@example.com",
                "$2a$10$abcdefghijklmnopqrstuv"
            );

        when(repository.existsByEmail(any()))
            .thenReturn(false);

        when(repository.save(any()))
            .thenThrow(new RuntimeException("Database unavailable"));

        assertThatThrownBy(() -> service.register(command))
            .isInstanceOf(RuntimeException.class)
            .hasMessage("Database unavailable");
    }
}
