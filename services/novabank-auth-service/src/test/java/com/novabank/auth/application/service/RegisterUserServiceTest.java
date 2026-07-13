package com.novabank.auth.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.novabank.auth.application.command.RegisterUserCommand;
import com.novabank.auth.application.exception.DuplicateEmailException;
import com.novabank.auth.application.port.security.PasswordEncoder;
import com.novabank.auth.application.response.RegisterUserResponse;
import com.novabank.auth.domain.model.User;
import com.novabank.auth.domain.model.UserStatus;
import com.novabank.auth.domain.repository.UserRepository;
import com.novabank.auth.domain.valueobject.EmailAddress;
import com.novabank.auth.domain.valueobject.PasswordHash;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RegisterUserServiceTest {

    private static final String EMAIL = "baldev@example.com";
    private static final String RAW_PASSWORD = "Password@123";
    private static final String ENCODED_PASSWORD =
        "$2a$10$abcdefghijklmnopqrstuv";

    @Mock
    private UserRepository repository;

    @Mock
    private PasswordEncoder passwordEncoder;

    private RegisterUserService service;

    @BeforeEach
    void setUp() {
        service = new RegisterUserService(repository, passwordEncoder);
    }

    @Test
    @DisplayName("Should register user successfully")
    void shouldRegisterUserSuccessfully() {

        RegisterUserCommand command = createCommand();

        User user = User.register(
            EmailAddress.of(command.email()),
            PasswordHash.of(ENCODED_PASSWORD)
        );

        when(repository.existsByEmail(any(EmailAddress.class)))
            .thenReturn(false);

        when(passwordEncoder.encode(RAW_PASSWORD))
            .thenReturn(ENCODED_PASSWORD);

        when(repository.save(any(User.class)))
            .thenReturn(user);

        RegisterUserResponse response = service.register(command);

        assertThat(response).isNotNull();
        assertThat(response.userId()).isEqualTo(user.id().value());
        assertThat(response.email()).isEqualTo(user.email().value());
        assertThat(response.status())
            .isEqualTo(UserStatus.PENDING_VERIFICATION.toString());

        verify(repository).existsByEmail(any(EmailAddress.class));
        verify(passwordEncoder).encode(RAW_PASSWORD);
        verify(repository).save(any(User.class));

        verifyNoMoreInteractions(repository, passwordEncoder);
    }

    @Test
    @DisplayName("Should reject null command")
    void shouldRejectNullCommand() {

        assertThatThrownBy(() -> service.register(null))
            .isInstanceOf(NullPointerException.class)
            .hasMessage("command cannot be null");

        verifyNoMoreInteractions(repository, passwordEncoder);
    }

    @Test
    @DisplayName("Should reject duplicate email")
    void shouldRejectDuplicateEmail() {

        RegisterUserCommand command = createCommand();

        when(repository.existsByEmail(any(EmailAddress.class)))
            .thenReturn(true);

        assertThatThrownBy(() -> service.register(command))
            .isInstanceOf(DuplicateEmailException.class)
            .hasMessage("Email already registered: " + EMAIL);

        verify(repository).existsByEmail(any(EmailAddress.class));
        verify(passwordEncoder, never()).encode(any());
        verify(repository, never()).save(any());

        verifyNoMoreInteractions(repository, passwordEncoder);
    }

    @Test
    @DisplayName("Should persist newly created user")
    void shouldPersistNewUser() {

        RegisterUserCommand command = createCommand();

        User user = User.register(
            EmailAddress.of(command.email()),
            PasswordHash.of(ENCODED_PASSWORD)
        );

        when(repository.existsByEmail(any()))
            .thenReturn(false);

        when(passwordEncoder.encode(RAW_PASSWORD))
            .thenReturn(ENCODED_PASSWORD);

        when(repository.save(any()))
            .thenReturn(user);

        service.register(command);

        verify(repository).save(any(User.class));
        verify(passwordEncoder).encode(RAW_PASSWORD);

        verifyNoMoreInteractions(repository, passwordEncoder);
    }

    @Test
    @DisplayName("Should encode password before saving user")
    void shouldEncodePasswordBeforeSavingUser() {

        RegisterUserCommand command = createCommand();

        when(repository.existsByEmail(any()))
            .thenReturn(false);

        when(passwordEncoder.encode(RAW_PASSWORD))
            .thenReturn(ENCODED_PASSWORD);

        User savedUser = User.register(
            EmailAddress.of(command.email()),
            PasswordHash.of(ENCODED_PASSWORD)
        );

        when(repository.save(any()))
            .thenReturn(savedUser);

        service.register(command);

        ArgumentCaptor<User> captor =
            ArgumentCaptor.forClass(User.class);

        verify(repository).save(captor.capture());

        User persistedUser = captor.getValue();

        assertThat(persistedUser.passwordHash().value())
            .isEqualTo(ENCODED_PASSWORD);

        verify(passwordEncoder).encode(RAW_PASSWORD);

        verifyNoMoreInteractions(repository, passwordEncoder);
    }

    @Test
    @DisplayName("Should check email uniqueness before saving user")
    void shouldCheckEmailUniquenessBeforeSavingUser() {

        RegisterUserCommand command = createCommand();

        User user = User.register(
            EmailAddress.of(command.email()),
            PasswordHash.of(ENCODED_PASSWORD)
        );

        when(repository.existsByEmail(any()))
            .thenReturn(false);

        when(passwordEncoder.encode(RAW_PASSWORD))
            .thenReturn(ENCODED_PASSWORD);

        when(repository.save(any()))
            .thenReturn(user);

        service.register(command);

        InOrder inOrder = inOrder(repository, passwordEncoder);

        inOrder.verify(repository)
            .existsByEmail(any(EmailAddress.class));

        inOrder.verify(passwordEncoder)
            .encode(RAW_PASSWORD);

        inOrder.verify(repository)
            .save(any(User.class));

        verifyNoMoreInteractions(repository, passwordEncoder);
    }

    @Test
    @DisplayName("Should normalize email before checking uniqueness")
    void shouldNormalizeEmailBeforeCheckingDuplicate() {

        RegisterUserCommand command =
            new RegisterUserCommand(
                "  BALDEV@EXAMPLE.COM  ",
                RAW_PASSWORD
            );

        when(repository.existsByEmail(any()))
            .thenReturn(false);

        when(passwordEncoder.encode(RAW_PASSWORD))
            .thenReturn(ENCODED_PASSWORD);

        User user = User.register(
            EmailAddress.of(command.email()),
            PasswordHash.of(ENCODED_PASSWORD)
        );

        when(repository.save(any()))
            .thenReturn(user);

        RegisterUserResponse response =
            service.register(command);

        assertThat(response.email())
            .isEqualTo("baldev@example.com");

        verify(repository)
            .existsByEmail(any(EmailAddress.class));

        verify(passwordEncoder)
            .encode(RAW_PASSWORD);

        verify(repository)
            .save(any(User.class));

        verifyNoMoreInteractions(repository, passwordEncoder);
    }

    @Test
    @DisplayName("Should propagate repository failure")
    void shouldPropagateRepositoryFailure() {

        RegisterUserCommand command = createCommand();

        when(repository.existsByEmail(any()))
            .thenReturn(false);

        when(passwordEncoder.encode(RAW_PASSWORD))
            .thenReturn(ENCODED_PASSWORD);

        when(repository.save(any()))
            .thenThrow(new RuntimeException("Database unavailable"));

        assertThatThrownBy(() -> service.register(command))
            .isInstanceOf(RuntimeException.class)
            .hasMessage("Database unavailable");

        verify(repository)
            .existsByEmail(any(EmailAddress.class));

        verify(passwordEncoder)
            .encode(RAW_PASSWORD);

        verify(repository)
            .save(any(User.class));

        verifyNoMoreInteractions(repository, passwordEncoder);
    }

    @Test
    @DisplayName("Should persist encoded password instead of raw password")
    void shouldPersistEncodedPassword() {

        RegisterUserCommand command = createCommand();

        when(repository.existsByEmail(any()))
            .thenReturn(false);

        when(passwordEncoder.encode(RAW_PASSWORD))
            .thenReturn(ENCODED_PASSWORD);

        User savedUser = User.register(
            EmailAddress.of(command.email()),
            PasswordHash.of(ENCODED_PASSWORD)
        );

        when(repository.save(any(User.class)))
            .thenReturn(savedUser);

        service.register(command);

        ArgumentCaptor<User> captor =
            ArgumentCaptor.forClass(User.class);

        verify(repository).save(captor.capture());

        User persistedUser = captor.getValue();

        assertThat(persistedUser.passwordHash().value())
            .isEqualTo(ENCODED_PASSWORD);

        assertThat(persistedUser.passwordHash().value())
            .isNotEqualTo(RAW_PASSWORD);

        verify(repository)
            .existsByEmail(any(EmailAddress.class));

        verify(passwordEncoder)
            .encode(RAW_PASSWORD);

        verifyNoMoreInteractions(repository, passwordEncoder);
    }

    private RegisterUserCommand createCommand() {
        return new RegisterUserCommand(
            EMAIL,
            RAW_PASSWORD
        );
    }

}
