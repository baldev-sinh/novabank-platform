package com.novabank.auth.application.service;

import com.novabank.auth.application.command.LoginUserCommand;
import com.novabank.auth.application.exception.InvalidCredentialsException;
import com.novabank.auth.application.port.security.PasswordEncoder;
import com.novabank.auth.application.port.security.TokenService;
import com.novabank.auth.application.response.LoginUserResponse;
import com.novabank.auth.application.security.AuthenticatedUser;
import com.novabank.auth.application.usecase.LoginUserUseCase;
import com.novabank.auth.domain.model.User;
import com.novabank.auth.domain.repository.UserRepository;
import com.novabank.auth.domain.valueobject.EmailAddress;
import java.util.Objects;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class LoginUserService implements LoginUserUseCase {

  private final UserRepository repository;
  private final PasswordEncoder passwordEncoder;
  private final TokenService tokenService;

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

    if (!passwordEncoder.matches(command.password(), user.passwordHash().value())) {
      throw invalidCredentials();
    }

    // Same generic message for locked/disabled accounts: don't tell an attacker the password was
    // right.
    if (!user.canSignIn()) {
      throw invalidCredentials();
    }

    String accessToken =
        tokenService.generateAccessToken(
            new AuthenticatedUser(user.id().value(), user.email().value(), user.roles()));

    return new LoginUserResponse(accessToken, "Bearer", tokenService.accessTokenExpiration());
  }

  private static InvalidCredentialsException invalidCredentials() {
    return new InvalidCredentialsException("Invalid email or password.");
  }
}
