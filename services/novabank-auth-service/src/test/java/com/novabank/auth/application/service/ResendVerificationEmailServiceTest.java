package com.novabank.auth.application.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.novabank.auth.application.port.persistence.EmailVerificationTokenStore;
import com.novabank.auth.application.verification.EmailVerificationSettings;
import com.novabank.auth.domain.model.RoleName;
import com.novabank.auth.domain.model.User;
import com.novabank.auth.domain.model.UserStatus;
import com.novabank.auth.domain.repository.UserRepository;
import com.novabank.auth.domain.valueobject.EmailAddress;
import com.novabank.auth.domain.valueobject.PasswordHash;
import com.novabank.auth.domain.valueobject.identifier.UserId;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ResendVerificationEmailServiceTest {

  private static final Instant NOW = Instant.parse("2026-11-16T10:00:00Z");
  private static final String EMAIL = "user@example.com";

  @Mock private UserRepository users;
  @Mock private EmailVerificationTokenStore tokens;
  @Mock private EmailVerificationIssuer issuer;

  private ResendVerificationEmailService service;

  @BeforeEach
  void setUp() {
    service =
        new ResendVerificationEmailService(
            users,
            tokens,
            issuer,
            new EmailVerificationSettings(Duration.ofHours(24), Duration.ofSeconds(60)),
            Clock.fixed(NOW, ZoneOffset.UTC));
  }

  @Test
  @DisplayName("Sends a new email to a pending user after the cooldown")
  void resendsAfterCooldown() {
    User user = user(UserStatus.PENDING_VERIFICATION);
    when(users.findByEmail(EmailAddress.of(EMAIL))).thenReturn(Optional.of(user));
    when(tokens.latestCreatedAt(user.id().value())).thenReturn(Optional.of(NOW.minusSeconds(60)));

    service.resend(EMAIL);

    verify(issuer).issueFor(user);
  }

  @Test
  @DisplayName("Does nothing within the cooldown")
  void respectsCooldown() {
    User user = user(UserStatus.PENDING_VERIFICATION);
    when(users.findByEmail(EmailAddress.of(EMAIL))).thenReturn(Optional.of(user));
    when(tokens.latestCreatedAt(user.id().value())).thenReturn(Optional.of(NOW.minusSeconds(59)));

    service.resend(EMAIL);

    verify(issuer, never()).issueFor(any());
  }

  @Test
  @DisplayName("Does nothing for unknown or already verified addresses (and doesn't say so)")
  void ignoresUnknownAndVerified() {
    when(users.findByEmail(EmailAddress.of(EMAIL)))
        .thenReturn(Optional.empty(), Optional.of(user(UserStatus.ACTIVE)));

    service.resend(EMAIL);
    service.resend(EMAIL);

    verify(issuer, never()).issueFor(any());
  }

  private static User user(UserStatus status) {
    return User.restore(
        UserId.random(),
        EmailAddress.of(EMAIL),
        PasswordHash.of("$2a$10$hash"),
        status,
        Set.of(RoleName.CUSTOMER),
        NOW,
        NOW,
        0L);
  }
}
