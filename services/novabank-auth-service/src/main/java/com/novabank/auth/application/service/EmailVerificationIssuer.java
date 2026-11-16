package com.novabank.auth.application.service;

import com.novabank.auth.application.port.persistence.EmailVerificationTokenStore;
import com.novabank.auth.application.verification.EmailVerificationSettings;
import com.novabank.auth.application.verification.VerificationEmailRequested;
import com.novabank.auth.application.verification.VerificationToken;
import com.novabank.auth.domain.model.User;
import java.time.Clock;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Issues a new verification token for a user and asks for the email to be sent after commit. */
@Component
@RequiredArgsConstructor
public class EmailVerificationIssuer {

  private final EmailVerificationTokenStore tokens;
  private final EmailVerificationSettings settings;
  private final ApplicationEventPublisher events;
  private final Clock clock;

  /**
   * Part of the caller's transaction: if it rolls back, no token is stored and no email is sent.
   */
  @Transactional(propagation = Propagation.MANDATORY)
  public void issueFor(User user) {
    Instant now = clock.instant();
    VerificationToken token = VerificationToken.generate();

    tokens.deleteUnusedFor(user.id().value());
    tokens.save(user.id().value(), token.hash(), now, now.plus(settings.tokenTtl()));

    events.publishEvent(
        new VerificationEmailRequested(user.id().value(), user.email().value(), token.value()));
  }
}
