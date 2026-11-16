package com.novabank.auth.application.service;

import com.novabank.auth.application.port.persistence.EmailVerificationTokenStore;
import com.novabank.auth.application.usecase.ResendVerificationEmailUseCase;
import com.novabank.auth.application.verification.EmailVerificationSettings;
import com.novabank.auth.domain.model.User;
import com.novabank.auth.domain.repository.UserRepository;
import com.novabank.auth.domain.valueobject.EmailAddress;
import java.time.Clock;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ResendVerificationEmailService implements ResendVerificationEmailUseCase {

  private final UserRepository users;
  private final EmailVerificationTokenStore tokens;
  private final EmailVerificationIssuer issuer;
  private final EmailVerificationSettings settings;
  private final Clock clock;

  @Override
  @Transactional
  public void resend(String email) {
    Objects.requireNonNull(email, "email cannot be null");

    users
        .findByEmail(EmailAddress.of(email))
        .filter(User::isPendingVerification)
        .filter(this::cooldownPassed)
        .ifPresent(issuer::issueFor);
  }

  /** Limits how often one mailbox can be sent emails through this endpoint. */
  private boolean cooldownPassed(User user) {
    return tokens
        .latestCreatedAt(user.id().value())
        .map(last -> !clock.instant().isBefore(last.plus(settings.resendCooldown())))
        .orElse(true);
  }
}
