package com.novabank.auth.application.service;

import com.novabank.auth.application.command.LoginUserCommand;
import com.novabank.auth.application.exception.EmailNotVerifiedException;
import com.novabank.auth.application.exception.InvalidCredentialsException;
import com.novabank.auth.application.port.persistence.LoginAttemptStore;
import com.novabank.auth.application.port.persistence.LoginAttemptStore.LoginAttempts;
import com.novabank.auth.application.port.security.PasswordEncoder;
import com.novabank.auth.application.port.security.TokenService;
import com.novabank.auth.application.response.LoginUserResponse;
import com.novabank.auth.application.security.AuthenticatedUser;
import com.novabank.auth.application.security.LoginProtectionSettings;
import com.novabank.auth.application.usecase.LoginUserUseCase;
import com.novabank.auth.domain.model.User;
import com.novabank.auth.domain.repository.UserRepository;
import com.novabank.auth.domain.valueobject.EmailAddress;
import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class LoginUserService implements LoginUserUseCase {

  private static final Logger LOGGER = LoggerFactory.getLogger(LoginUserService.class);

  private final UserRepository repository;
  private final PasswordEncoder passwordEncoder;
  private final TokenService tokenService;
  private final LoginAttemptStore loginAttempts;
  private final LoginProtectionSettings protection;
  private final Clock clock;

  @Override
  public LoginUserResponse login(LoginUserCommand command) {

    Objects.requireNonNull(command, "command cannot be null");

    EmailAddress email = EmailAddress.of(command.email());
    Optional<User> found = repository.findByEmail(email);

    if (found.isEmpty()) {
      // Same BCrypt cost as a real check, so timing doesn't reveal which emails exist.
      passwordEncoder.simulateMatch(command.password());
      throw invalidCredentials();
    }

    User user = found.get();
    UUID userId = user.id().value();
    Instant now = clock.instant();
    Optional<LoginAttempts> attempts = loginAttempts.find(userId);

    // Always pay the BCrypt cost, so a locked account answers exactly like a wrong password.
    boolean passwordMatches =
        passwordEncoder.matches(command.password(), user.passwordHash().value());

    // While locked, even the right password is refused, and the attempt doesn't extend the lock.
    if (attempts.filter(a -> a.isLockedAt(now)).isPresent()) {
      throw invalidCredentials();
    }

    if (!passwordMatches) {
      recordFailure(userId, now);
      throw invalidCredentials();
    }

    if (attempts.isPresent()) {
      loginAttempts.reset(userId);
    }

    // Only said after a correct password, so the client can offer "resend verification email".
    if (user.isPendingVerification()) {
      throw new EmailNotVerifiedException();
    }

    // Same generic message for locked/disabled accounts: don't tell an attacker the password was
    // right.
    if (!user.canSignIn()) {
      throw invalidCredentials();
    }

    String accessToken =
        tokenService.generateAccessToken(
            new AuthenticatedUser(userId, user.email().value(), user.roles()));

    return new LoginUserResponse(accessToken, "Bearer", tokenService.accessTokenExpiration());
  }

  private void recordFailure(UUID userId, Instant now) {
    LoginAttempts after =
        loginAttempts.recordFailure(
            userId,
            now,
            now.minus(protection.failureWindow()),
            protection.maxFailedAttempts(),
            now.plus(protection.lockoutDuration()));
    if (after.isLockedAt(now)) {
      // Security signal for monitoring; the user id only, never the email.
      LOGGER.warn(
          "Account {} locked until {} after {} failed sign-ins",
          userId,
          after.lockedUntil(),
          protection.maxFailedAttempts());
    }
  }

  private static InvalidCredentialsException invalidCredentials() {
    return new InvalidCredentialsException("Invalid email or password.");
  }
}
