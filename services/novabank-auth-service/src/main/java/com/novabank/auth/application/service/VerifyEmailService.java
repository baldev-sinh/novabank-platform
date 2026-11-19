package com.novabank.auth.application.service;

import com.novabank.auth.application.exception.InvalidVerificationTokenException;
import com.novabank.auth.application.port.persistence.EmailVerificationTokenStore;
import com.novabank.auth.application.security.BootstrapAdmin;
import com.novabank.auth.application.usecase.VerifyEmailUseCase;
import com.novabank.auth.application.verification.VerificationToken;
import com.novabank.auth.domain.model.User;
import com.novabank.auth.domain.repository.UserRepository;
import com.novabank.auth.domain.valueobject.identifier.UserId;
import java.time.Clock;
import java.util.Objects;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class VerifyEmailService implements VerifyEmailUseCase {

  private static final Logger LOGGER = LoggerFactory.getLogger(VerifyEmailService.class);

  private final EmailVerificationTokenStore tokens;
  private final UserRepository users;
  private final Clock clock;
  private final BootstrapAdmin bootstrapAdmin;

  /** Consuming the token and activating the user commit together, or not at all. */
  @Override
  @Transactional
  public void verify(String token) {
    Objects.requireNonNull(token, "token cannot be null");

    UUID userId =
        tokens
            .consume(VerificationToken.hashOf(token), clock.instant())
            .orElseThrow(InvalidVerificationTokenException::new);

    User user =
        users.findById(UserId.of(userId)).orElseThrow(InvalidVerificationTokenException::new);
    // E.g. disabled after the email was sent: an old link must not reactivate the account.
    if (!user.isPendingVerification()) {
      throw new InvalidVerificationTokenException();
    }

    user.activate();
    // The configured first administrator becomes ADMIN the moment their address is verified.
    if (bootstrapAdmin.applyTo(user)) {
      LOGGER.info("Bootstrap admin: ADMIN granted to user {}", userId);
    }
    users.save(user);
  }
}
