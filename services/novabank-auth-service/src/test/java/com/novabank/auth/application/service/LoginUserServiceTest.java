package com.novabank.auth.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.novabank.auth.application.command.LoginUserCommand;
import com.novabank.auth.application.exception.InvalidCredentialsException;
import com.novabank.auth.application.port.security.PasswordEncoder;
import com.novabank.auth.application.port.security.TokenService;
import com.novabank.auth.application.response.LoginUserResponse;
import com.novabank.auth.application.security.JwtUser;
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
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class LoginUserServiceTest {

    private static final UserId USER_ID = UserId.random();

    private static final String EMAIL =
        "baldev@example.com";

    private static final String RAW_PASSWORD =
        "Password@123";

    private static final String ENCODED_PASSWORD =
        "$2a$10$abcdefghijklmnopqrstuv";

    private static final String ACCESS_TOKEN =
        "jwt-access-token";

    private static final long EXPIRES_IN = 900L;

    private static final Instant CREATED_AT =
        Instant.now();

    private static final Instant UPDATED_AT =
        Instant.now();

    @Mock
    private UserRepository repository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private TokenService tokenService;

    private LoginUserService service;

    @BeforeEach
    void setUp() {

        service = new LoginUserService(
            repository,
            passwordEncoder,
            tokenService
        );
    }

    @Test
    @DisplayName("Should authenticate user successfully")
    void shouldAuthenticateSuccessfully() {

        LoginUserCommand command =
            createCommand();

        User user = createUser();

        when(repository.findByEmail(any()))
            .thenReturn(Optional.of(user));

        when(passwordEncoder.matches(
            RAW_PASSWORD,
            ENCODED_PASSWORD))
            .thenReturn(true);

        when(tokenService.generateAccessToken(any()))
            .thenReturn(ACCESS_TOKEN);

        when(tokenService.accessTokenExpiration())
            .thenReturn(EXPIRES_IN);

        LoginUserResponse response =
            service.login(command);

        assertThat(response).isNotNull();

        assertThat(response.accessToken())
            .isEqualTo(ACCESS_TOKEN);

        assertThat(response.tokenType())
            .isEqualTo("Bearer");

        assertThat(response.expiresIn())
            .isEqualTo(EXPIRES_IN);

        verify(repository)
            .findByEmail(any());

        verify(passwordEncoder)
            .matches(
                RAW_PASSWORD,
                ENCODED_PASSWORD
            );

        verify(tokenService)
            .generateAccessToken(any());

        verify(tokenService)
            .accessTokenExpiration();

        verifyNoMoreInteractions(
            repository,
            passwordEncoder,
            tokenService
        );
    }

    @Test
    @DisplayName("Should build JwtUser before generating token")
    void shouldBuildJwtUser() {

        User user = createUser();

        when(repository.findByEmail(any()))
            .thenReturn(Optional.of(user));

        when(passwordEncoder.matches(
            RAW_PASSWORD,
            ENCODED_PASSWORD))
            .thenReturn(true);

        when(tokenService.generateAccessToken(any()))
            .thenReturn(ACCESS_TOKEN);

        when(tokenService.accessTokenExpiration())
            .thenReturn(EXPIRES_IN);

        service.login(createCommand());

        ArgumentCaptor<JwtUser> captor =
            ArgumentCaptor.forClass(
                JwtUser.class
            );

        verify(tokenService)
            .generateAccessToken(
                captor.capture()
            );

        JwtUser jwtUser =
            captor.getValue();

        assertThat(jwtUser.userId())
            .isEqualTo(USER_ID.value());

        assertThat(jwtUser.email())
            .isEqualTo(EMAIL);

        assertThat(jwtUser.roles())
            .containsExactly(RoleName.CUSTOMER);

        verify(repository)
            .findByEmail(any());

        verify(passwordEncoder)
            .matches(
                RAW_PASSWORD,
                ENCODED_PASSWORD
            );

        verify(tokenService)
            .accessTokenExpiration();

        verifyNoMoreInteractions(
            repository,
            passwordEncoder,
            tokenService
        );
    }

    @Test
    @DisplayName("Should reject null command")
    void shouldRejectNullCommand() {

        assertThatThrownBy(() ->
            service.login(null))
            .isInstanceOf(
                NullPointerException.class
            )
            .hasMessage(
                "command cannot be null"
            );

        verifyNoInteractions(
            repository,
            passwordEncoder,
            tokenService
        );
    }

    @Test
    @DisplayName("Should reject unknown email")
    void shouldRejectUnknownEmail() {

        LoginUserCommand command = createCommand();

        when(repository.findByEmail(any(EmailAddress.class)))
            .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.login(command))
            .isInstanceOf(InvalidCredentialsException.class)
            .hasMessage("Invalid email or password.");

        verify(repository)
            .findByEmail(any(EmailAddress.class));

        verify(passwordEncoder, never())
            .matches(any(), any());

        verify(tokenService, never())
            .generateAccessToken(any());

        verify(tokenService, never())
            .accessTokenExpiration();

        verifyNoMoreInteractions(
            repository,
            passwordEncoder,
            tokenService
        );
    }

    @Test
    @DisplayName("Should reject invalid password")
    void shouldRejectInvalidPassword() {

        LoginUserCommand command = createCommand();

        User user = createUser();

        when(repository.findByEmail(any(EmailAddress.class)))
            .thenReturn(Optional.of(user));

        when(passwordEncoder.matches(
            RAW_PASSWORD,
            ENCODED_PASSWORD))
            .thenReturn(false);

        assertThatThrownBy(() -> service.login(command))
            .isInstanceOf(InvalidCredentialsException.class)
            .hasMessage("Invalid email or password.");

        verify(repository)
            .findByEmail(any(EmailAddress.class));

        verify(passwordEncoder)
            .matches(
                RAW_PASSWORD,
                ENCODED_PASSWORD
            );

        verify(tokenService, never())
            .generateAccessToken(any());

        verify(tokenService, never())
            .accessTokenExpiration();

        verifyNoMoreInteractions(
            repository,
            passwordEncoder,
            tokenService
        );
    }

    @Test
    @DisplayName("Should verify interaction order")
    void shouldVerifyInteractionOrder() {

        LoginUserCommand command = createCommand();

        User user = createUser();

        when(repository.findByEmail(any(EmailAddress.class)))
            .thenReturn(Optional.of(user));

        when(passwordEncoder.matches(
            RAW_PASSWORD,
            ENCODED_PASSWORD))
            .thenReturn(true);

        when(tokenService.generateAccessToken(any(JwtUser.class)))
            .thenReturn(ACCESS_TOKEN);

        when(tokenService.accessTokenExpiration())
            .thenReturn(EXPIRES_IN);

        service.login(command);

        InOrder inOrder = inOrder(
            repository,
            passwordEncoder,
            tokenService
        );

        inOrder.verify(repository)
            .findByEmail(any(EmailAddress.class));

        inOrder.verify(passwordEncoder)
            .matches(
                RAW_PASSWORD,
                ENCODED_PASSWORD
            );

        inOrder.verify(tokenService)
            .generateAccessToken(any(JwtUser.class));

        inOrder.verify(tokenService)
            .accessTokenExpiration();

        verifyNoMoreInteractions(
            repository,
            passwordEncoder,
            tokenService
        );
    }

    @Test
    @DisplayName("Should normalize email before lookup")
    void shouldNormalizeEmailBeforeLookup() {

        LoginUserCommand command =
            new LoginUserCommand(
                "  BALDEV@EXAMPLE.COM ",
                RAW_PASSWORD
            );

        User user = createUser();

        when(repository.findByEmail(
            EmailAddress.of("baldev@example.com")))
            .thenReturn(Optional.of(user));

        when(passwordEncoder.matches(
            RAW_PASSWORD,
            ENCODED_PASSWORD))
            .thenReturn(true);

        when(tokenService.generateAccessToken(any()))
            .thenReturn(ACCESS_TOKEN);

        when(tokenService.accessTokenExpiration())
            .thenReturn(EXPIRES_IN);

        LoginUserResponse response =
            service.login(command);

        assertThat(response.accessToken())
            .isEqualTo(ACCESS_TOKEN);

        verify(repository)
            .findByEmail(
                EmailAddress.of("baldev@example.com")
            );

        verify(passwordEncoder)
            .matches(
                RAW_PASSWORD,
                ENCODED_PASSWORD
            );

        verify(tokenService)
            .generateAccessToken(any());

        verify(tokenService)
            .accessTokenExpiration();

        verifyNoMoreInteractions(
            repository,
            passwordEncoder,
            tokenService
        );
    }

    @Test
    @DisplayName("Should propagate repository failure")
    void shouldPropagateRepositoryFailure() {

        LoginUserCommand command = createCommand();

        when(repository.findByEmail(any(EmailAddress.class)))
            .thenThrow(new RuntimeException("Database unavailable"));

        assertThatThrownBy(() -> service.login(command))
            .isInstanceOf(RuntimeException.class)
            .hasMessage("Database unavailable");

        verify(repository)
            .findByEmail(any(EmailAddress.class));

        verify(passwordEncoder, never())
            .matches(any(), any());

        verify(tokenService, never())
            .generateAccessToken(any());

        verify(tokenService, never())
            .accessTokenExpiration();

        verifyNoMoreInteractions(
            repository,
            passwordEncoder,
            tokenService
        );
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
