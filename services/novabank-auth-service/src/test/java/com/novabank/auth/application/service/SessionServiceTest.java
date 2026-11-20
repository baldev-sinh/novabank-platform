package com.novabank.auth.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.novabank.auth.application.exception.InvalidRefreshTokenException;
import com.novabank.auth.application.port.persistence.RefreshTokenStore;
import com.novabank.auth.application.port.persistence.RefreshTokenStore.Consumed;
import com.novabank.auth.application.port.persistence.RefreshTokenStore.Stored;
import com.novabank.auth.application.port.security.TokenService;
import com.novabank.auth.application.response.LoginUserResponse;
import com.novabank.auth.application.security.RefreshTokenSettings;
import com.novabank.auth.application.security.RefreshTokenValue;
import com.novabank.auth.application.service.RefreshTokenIssuer.IssuedRefreshToken;
import com.novabank.auth.domain.model.User;
import com.novabank.auth.domain.repository.UserRepository;
import com.novabank.auth.domain.valueobject.EmailAddress;
import com.novabank.auth.domain.valueobject.PasswordHash;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SessionServiceTest {

  private static final Instant NOW = Instant.parse("2026-11-20T10:00:00Z");
  private static final RefreshTokenSettings SETTINGS =
      new RefreshTokenSettings(Duration.ofMinutes(30), Duration.ofHours(8), Duration.ofSeconds(10));
  private static final PasswordHash HASH =
      PasswordHash.of("$2a$10$yhjkvDQLAQTS9ymYWnwjP.sL6CxMrvs9ZKgvjyLhDvOtU83fpK2KO");
  private static final String TOKEN = "the-refresh-token";

  @Mock private RefreshTokenStore tokens;
  @Mock private RefreshTokenIssuer issuer;
  @Mock private UserRepository users;
  @Mock private TokenService tokenService;

  private SessionService service;

  @BeforeEach
  void setUp() {
    service =
        new SessionService(
            tokens, issuer, SETTINGS, users, tokenService, Clock.fixed(NOW, ZoneOffset.UTC));
  }

  @Test
  @DisplayName("A valid token is rotated: new access token and the next token in the same family")
  void rotates() {
    User user = activeUser();
    UUID family = UUID.randomUUID();
    Instant familyEnd = NOW.plus(Duration.ofHours(7));
    when(tokens.consume(RefreshTokenValue.hashOf(TOKEN), NOW))
        .thenReturn(Optional.of(new Consumed(user.id().value(), family, familyEnd)));
    when(users.findById(user.id())).thenReturn(Optional.of(user));
    when(tokenService.generateAccessToken(any())).thenReturn("access");
    when(tokenService.accessTokenExpiration()).thenReturn(900L);
    IssuedRefreshToken next = new IssuedRefreshToken("next", familyEnd);
    when(issuer.rotate(user.id().value(), family, familyEnd)).thenReturn(next);

    LoginUserResponse response = service.refresh(TOKEN);

    assertThat(response.accessToken()).isEqualTo("access");
    assertThat(response.refreshToken()).isEqualTo(next);
    verify(tokens, never()).revokeFamily(any(), any());
  }

  @Test
  @DisplayName("A token used again after the grace period revokes the whole sign-in")
  void reuseRevokesFamily() {
    UUID family = UUID.randomUUID();
    when(tokens.consume(any(), any())).thenReturn(Optional.empty());
    when(tokens.find(RefreshTokenValue.hashOf(TOKEN)))
        .thenReturn(Optional.of(new Stored(UUID.randomUUID(), family, NOW.minusSeconds(60), null)));

    assertThatThrownBy(() -> service.refresh(TOKEN))
        .isInstanceOf(InvalidRefreshTokenException.class);

    verify(tokens).revokeFamily(family, NOW);
  }

  @Test
  @DisplayName("A token used a moment ago (two tabs refreshing) is refused without revoking")
  void reuseWithinGraceDoesNotRevoke() {
    when(tokens.consume(any(), any())).thenReturn(Optional.empty());
    when(tokens.find(any()))
        .thenReturn(
            Optional.of(
                new Stored(UUID.randomUUID(), UUID.randomUUID(), NOW.minusSeconds(3), null)));

    assertThatThrownBy(() -> service.refresh(TOKEN))
        .isInstanceOf(InvalidRefreshTokenException.class);

    verify(tokens, never()).revokeFamily(any(), any());
  }

  @Test
  @DisplayName("An unknown, expired or revoked token is refused and revokes nothing")
  void unknownTokenIsRefused() {
    when(tokens.consume(any(), any())).thenReturn(Optional.empty());
    when(tokens.find(any())).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.refresh(TOKEN))
        .isInstanceOf(InvalidRefreshTokenException.class);

    verify(tokens, never()).revokeFamily(any(), any());
  }

  @Test
  @DisplayName("A user locked since signing in can't refresh, and the sign-in is revoked")
  void lockedUserCannotRefresh() {
    User user = activeUser();
    user.lock();
    UUID family = UUID.randomUUID();
    when(tokens.consume(any(), any()))
        .thenReturn(Optional.of(new Consumed(user.id().value(), family, NOW.plusSeconds(60))));
    when(users.findById(user.id())).thenReturn(Optional.of(user));

    assertThatThrownBy(() -> service.refresh(TOKEN))
        .isInstanceOf(InvalidRefreshTokenException.class);

    verify(tokens).revokeFamily(family, NOW);
  }

  @Test
  @DisplayName("Logout revokes the token's sign-in; no token or an unknown one is a no-op")
  void logout() {
    UUID family = UUID.randomUUID();
    when(tokens.find(RefreshTokenValue.hashOf(TOKEN)))
        .thenReturn(Optional.of(new Stored(UUID.randomUUID(), family, null, null)));

    service.logout(TOKEN);
    service.logout(null);
    service.logout(" ");

    verify(tokens).revokeFamily(family, NOW);
  }

  private static User activeUser() {
    User user = User.register(EmailAddress.of("user-" + UUID.randomUUID() + "@example.com"), HASH);
    user.activate();
    return user;
  }
}
