package com.novabank.auth.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.novabank.auth.application.exception.InvalidVerificationTokenException;
import com.novabank.auth.application.port.persistence.EmailVerificationTokenStore;
import com.novabank.auth.application.verification.VerificationToken;
import com.novabank.auth.domain.model.RoleName;
import com.novabank.auth.domain.model.User;
import com.novabank.auth.domain.model.UserStatus;
import com.novabank.auth.domain.repository.UserRepository;
import com.novabank.auth.domain.valueobject.EmailAddress;
import com.novabank.auth.domain.valueobject.PasswordHash;
import com.novabank.auth.domain.valueobject.identifier.UserId;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class VerifyEmailServiceTest {

  private static final Instant NOW = Instant.parse("2026-11-16T10:00:00Z");
  private static final UUID USER_ID = UUID.randomUUID();
  private static final String TOKEN = "raw-token";

  @Mock private EmailVerificationTokenStore tokens;
  @Mock private UserRepository users;

  private VerifyEmailService service;

  @BeforeEach
  void setUp() {
    service = new VerifyEmailService(tokens, users, Clock.fixed(NOW, ZoneOffset.UTC));
  }

  @Test
  @DisplayName("A valid token activates the pending user")
  void activatesUser() {
    when(tokens.consume(VerificationToken.hashOf(TOKEN), NOW)).thenReturn(Optional.of(USER_ID));
    when(users.findById(UserId.of(USER_ID)))
        .thenReturn(Optional.of(user(UserStatus.PENDING_VERIFICATION)));

    service.verify(TOKEN);

    ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
    verify(users).save(saved.capture());
    assertThat(saved.getValue().status()).isEqualTo(UserStatus.ACTIVE);
  }

  @Test
  @DisplayName("An unknown, used or expired token is rejected")
  void rejectsUnusableToken() {
    when(tokens.consume(any(), any())).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.verify(TOKEN))
        .isInstanceOf(InvalidVerificationTokenException.class);
    verify(users, never()).save(any());
  }

  @Test
  @DisplayName("An old link can't reactivate a user who was disabled meanwhile")
  void rejectsUserNoLongerPending() {
    when(tokens.consume(any(), any())).thenReturn(Optional.of(USER_ID));
    when(users.findById(UserId.of(USER_ID))).thenReturn(Optional.of(user(UserStatus.DISABLED)));

    assertThatThrownBy(() -> service.verify(TOKEN))
        .isInstanceOf(InvalidVerificationTokenException.class);
    verify(users, never()).save(any());
  }

  private static User user(UserStatus status) {
    return User.restore(
        UserId.of(USER_ID),
        EmailAddress.of("user@example.com"),
        PasswordHash.of("$2a$10$hash"),
        status,
        Set.of(RoleName.CUSTOMER),
        NOW,
        NOW,
        0L);
  }
}
