package com.novabank.auth.application.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.any;
import static org.assertj.core.api.Assertions.assertThat;

import com.novabank.auth.application.command.LoginUserCommand;
import com.novabank.auth.application.exception.InvalidCredentialsException;
import com.novabank.auth.application.port.security.PasswordEncoder;
import com.novabank.auth.application.response.LoginUserResponse;
import com.novabank.auth.domain.model.RoleName;
import com.novabank.auth.domain.model.User;
import com.novabank.auth.domain.model.UserStatus;
import com.novabank.auth.domain.repository.UserRepository;
import com.novabank.auth.domain.valueobject.EmailAddress;
import com.novabank.auth.domain.valueobject.PasswordHash;
import com.novabank.auth.domain.valueobject.identifier.UserId;
import java.time.Instant;
import java.util.EnumSet;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class LoginUserServiceTest {

    private static final UserId USER_ID = UserId.random();
    private static final String EMAIL = "baldev@example.com";
    private static final String RAW_PASSWORD = "Password@123";
    private static final String ENCODED_PASSWORD = "$2a$10$abcdefghijklmnopqrstuv";
    private static final Instant CREATED_AT = Instant.now();
    private static final Instant UPDATED_AT = Instant.now();

    @Mock
    private UserRepository repository;

    @Mock
    private PasswordEncoder passwordEncoder;

    private LoginUserService service;

    @BeforeEach
    void setUp(){
        service = new LoginUserService(repository, passwordEncoder);
    }

    @Test
    @DisplayName("Should authenticate user successfully")
    void shouldAuthenticateSuccessfully(){
        LoginUserCommand command = createCommand();

        User user = createUser();

        when(repository.findByEmail(any(EmailAddress.class)))
            .thenReturn(Optional.of(user));

        when(passwordEncoder.matches(RAW_PASSWORD, ENCODED_PASSWORD))
            .thenReturn(true);

        LoginUserResponse response = service.login(command);

        assertThat(response).isNotNull();
        assertThat(response.userId()).isEqualTo(USER_ID.value());
        assertThat(response.email()).isEqualTo(EMAIL);
        assertThat(response.status()).isEqualTo(UserStatus.ACTIVE.name());

        verify(repository).findByEmail(any(EmailAddress.class));
        verify(passwordEncoder)
            .matches(RAW_PASSWORD, ENCODED_PASSWORD);

        verifyNoMoreInteractions(repository, passwordEncoder);

    }

    @Test
    @DisplayName("Should reject null command")
    void shouldRejectNullCommand(){
        assertThatThrownBy(() -> service.login(null))
            .isInstanceOf(NullPointerException.class)
            .hasMessage("command cannot be null");
    }

    @Test
    @DisplayName("Should reject unknown email")
    void shouldRejectUnknownEmail(){
        LoginUserCommand command = createCommand();

        when(repository.findByEmail(any(EmailAddress.class)))
            .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.login(command))
            .isInstanceOf(InvalidCredentialsException.class)
            .hasMessage("Invalid email or password.");

        verify(repository).findByEmail(any(EmailAddress.class));
        verify(passwordEncoder, never()).matches(any(), any());

        verifyNoMoreInteractions(repository, passwordEncoder);
    }

    @Test
    @DisplayName("Should reject invalid password")
    void shouldRejectInvalidPassword(){
        LoginUserCommand command = createCommand();
        User user = createUser();

        when(repository.findByEmail(any(EmailAddress.class)))
            .thenReturn(Optional.of(user));

        when(passwordEncoder.matches(RAW_PASSWORD, ENCODED_PASSWORD))
            .thenReturn(false);

        assertThatThrownBy(() -> service.login(command))
            .isInstanceOf(InvalidCredentialsException.class)
                .hasMessage("Invalid email or password.");

        verify(repository).findByEmail(any(EmailAddress.class));

        verify(passwordEncoder)
            .matches(RAW_PASSWORD, ENCODED_PASSWORD);

        verifyNoMoreInteractions(repository, passwordEncoder);
    }

    @Test
    @DisplayName("Should normalize email before repository lookup")
    void shouldNormalizeEmailBeforeLookup() {

        LoginUserCommand command =
            new LoginUserCommand(
                "  BALDEV@EXAMPLE.COM  ",
                RAW_PASSWORD
            );

        User user = createUser();

        when(repository.findByEmail(any(EmailAddress.class)))
            .thenReturn(Optional.of(user));

        when(passwordEncoder.matches(
            RAW_PASSWORD,
            ENCODED_PASSWORD))
            .thenReturn(true);

        service.login(command);

        verify(repository)
            .findByEmail(EmailAddress.of("baldev@example.com"));

        verify(passwordEncoder)
            .matches(RAW_PASSWORD, ENCODED_PASSWORD);

        verifyNoMoreInteractions(repository, passwordEncoder);
    }

    @Test
    @DisplayName("Should verify interaction order")
    void shouldVerifyInteractionOrder() {

        LoginUserCommand command = createCommand();

        User user = createUser();

        when(repository.findByEmail(any()))
            .thenReturn(Optional.of(user));

        when(passwordEncoder.matches(
            RAW_PASSWORD,
            ENCODED_PASSWORD))
            .thenReturn(true);

        service.login(command);

        InOrder inOrder = inOrder(repository, passwordEncoder);

        inOrder.verify(repository).findByEmail(any(EmailAddress.class));
        inOrder.verify(passwordEncoder).matches(RAW_PASSWORD, ENCODED_PASSWORD);

        verifyNoMoreInteractions(repository, passwordEncoder);
    }

    @Test
    @DisplayName("Should propagate repository failure")
    void shouldPropagateRepositoryFailure() {

        LoginUserCommand command = createCommand();

        when(repository.findByEmail(any()))
            .thenThrow(new RuntimeException("Database unavailable"));

        assertThatThrownBy(() -> service.login(command))
            .isInstanceOf(RuntimeException.class)
            .hasMessage("Database unavailable");

        verify(repository).findByEmail(any(EmailAddress.class));

        verify(passwordEncoder, never()).matches(any(), any());

        verifyNoMoreInteractions(repository, passwordEncoder);
    }

    private LoginUserCommand createCommand() {
        return new LoginUserCommand(
            EMAIL,
            RAW_PASSWORD
        );
    }

    private User createUser() {
        return User.restore(
            USER_ID,
            EmailAddress.of(EMAIL),
            PasswordHash.of(ENCODED_PASSWORD),
            UserStatus.ACTIVE,
            EnumSet.of(RoleName.CUSTOMER),
            CREATED_AT,
            UPDATED_AT
        );
    }
}
