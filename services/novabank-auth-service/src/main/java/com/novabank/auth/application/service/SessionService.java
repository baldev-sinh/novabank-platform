package com.novabank.auth.application.service;

import com.novabank.auth.application.exception.InvalidRefreshTokenException;
import com.novabank.auth.application.port.persistence.RefreshTokenStore;
import com.novabank.auth.application.port.persistence.RefreshTokenStore.Consumed;
import com.novabank.auth.application.port.security.TokenService;
import com.novabank.auth.application.response.LoginUserResponse;
import com.novabank.auth.application.security.AuthenticatedUser;
import com.novabank.auth.application.security.RefreshTokenSettings;
import com.novabank.auth.application.security.RefreshTokenValue;
import com.novabank.auth.application.service.RefreshTokenIssuer.IssuedRefreshToken;
import com.novabank.auth.application.usecase.SessionUseCase;
import com.novabank.auth.domain.model.User;
import com.novabank.auth.domain.repository.UserRepository;
import com.novabank.auth.domain.valueobject.identifier.UserId;
import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Refresh token rotation with reuse detection (RFC 9700 section 4.14). Each refresh reloads the
 * user, so a locked or disabled account can't refresh, and role changes reach the next access
 * token.
 */
@Service
@RequiredArgsConstructor
public class SessionService implements SessionUseCase {

  private static final Logger LOGGER = LoggerFactory.getLogger(SessionService.class);

  private final RefreshTokenStore tokens;
  private final RefreshTokenIssuer issuer;
  private final RefreshTokenSettings settings;
  private final UserRepository users;
  private final TokenService tokenService;
  private final Clock clock;

  /**
   * Doesn't roll back on {@link InvalidRefreshTokenException}: a revocation made while refusing
   * the token (reuse, locked user) must commit with the refusal. Everything runs in this one
   * transaction, so no second connection ever waits on a row this one has locked.
   */
  @Override
  @Transactional(noRollbackFor = InvalidRefreshTokenException.class)
  public LoginUserResponse refresh(String refreshToken) {
    Objects.requireNonNull(refreshToken, "refreshToken cannot be null");
    String hash = RefreshTokenValue.hashOf(refreshToken);
    Instant now = clock.instant();

    Optional<Consumed> consumed = tokens.consume(hash, now);
    if (consumed.isEmpty()) {
      detectReuse(hash, now);
      throw new InvalidRefreshTokenException();
    }
    Consumed session = consumed.get();

    Optional<User> user = users.findById(UserId.of(session.userId()));
    if (user.isEmpty() || !user.get().canSignIn()) {
      tokens.revokeFamily(session.familyId(), now);
      throw new InvalidRefreshTokenException();
    }

    User current = user.get();
    String accessToken =
        tokenService.generateAccessToken(
            new AuthenticatedUser(current.id().value(), current.email().value(), current.roles()));
    IssuedRefreshToken next =
        issuer.rotate(session.userId(), session.familyId(), session.familyExpiresAt());
    return new LoginUserResponse(accessToken, "Bearer", tokenService.accessTokenExpiration(), next);
  }

  @Override
  @Transactional
  public void logout(String refreshToken) {
    if (refreshToken == null || refreshToken.isBlank()) {
      return;
    }
    tokens
        .find(RefreshTokenValue.hashOf(refreshToken))
        .ifPresent(stored -> tokens.revokeFamily(stored.familyId(), clock.instant()));
  }

  /**
   * A used token presented again means it was copied: revoke the whole sign-in, so neither the
   * attacker nor the victim can continue with it. Within the grace period it's a race between two
   * tabs, and the other tab already holds the next token.
   */
  private void detectReuse(String hash, Instant now) {
    tokens
        .find(hash)
        .filter(stored -> stored.revokedAt() == null && stored.usedAt() != null)
        .filter(stored -> stored.usedAt().plus(settings.reuseGrace()).isBefore(now))
        .ifPresent(stored -> {
          tokens.revokeFamily(stored.familyId(), now);
          LOGGER.warn(
              "Refresh token reused for user {}: sign-in {} revoked",
              stored.userId(),
              stored.familyId());
        });
  }
}
