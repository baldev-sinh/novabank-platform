package com.novabank.auth.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;

import com.novabank.auth.application.port.persistence.EmailVerificationTokenStore;
import com.novabank.auth.application.verification.EmailVerificationSettings;
import com.novabank.auth.application.verification.VerificationEmailRequested;
import com.novabank.auth.application.verification.VerificationToken;
import com.novabank.auth.domain.model.User;
import com.novabank.auth.domain.valueobject.EmailAddress;
import com.novabank.auth.domain.valueobject.PasswordHash;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

@ExtendWith(MockitoExtension.class)
class EmailVerificationIssuerTest {

  private static final Instant NOW = Instant.parse("2026-11-16T10:00:00Z");

  @Mock private EmailVerificationTokenStore tokens;
  @Mock private ApplicationEventPublisher events;

  @Test
  @DisplayName("Replaces old tokens, stores only the hash and publishes the raw token in memory")
  void issuesToken() {
    EmailVerificationIssuer issuer =
        new EmailVerificationIssuer(
            tokens,
            new EmailVerificationSettings(Duration.ofHours(24), Duration.ofSeconds(60)),
            events,
            Clock.fixed(NOW, ZoneOffset.UTC));
    User user = User.register(EmailAddress.of("user@example.com"), PasswordHash.of("$2a$10$hash"));

    issuer.issueFor(user);

    ArgumentCaptor<VerificationEmailRequested> event =
        ArgumentCaptor.forClass(VerificationEmailRequested.class);
    verify(events).publishEvent(event.capture());
    String rawToken = event.getValue().token();
    assertThat(event.getValue().email()).isEqualTo("user@example.com");

    InOrder order = inOrder(tokens);
    order.verify(tokens).deleteUnusedFor(user.id().value());
    order
        .verify(tokens)
        .save(
            eq(user.id().value()),
            eq(VerificationToken.hashOf(rawToken)),
            eq(NOW),
            eq(NOW.plus(Duration.ofHours(24))));
  }
}
