package com.novabank.notification.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.novabank.notification.application.port.Directory;
import com.novabank.notification.application.port.Directory.KnownAccount;
import com.novabank.notification.application.port.EmailSender;
import com.novabank.notification.application.port.EmailSender.EmailDeliveryException;
import com.novabank.notification.application.port.NotificationStore;
import com.novabank.notification.domain.EmailMessage;
import com.novabank.notification.domain.Notification;
import com.novabank.notification.domain.NotificationKind;
import com.novabank.notification.infrastructure.configuration.NotificationProperties;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DispatchNotificationsServiceTest {

  private static final Instant NOW = Instant.parse("2026-11-23T10:00:00Z");
  private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);
  private static final NotificationProperties PROPERTIES =
      new NotificationProperties(
          "no-reply@novabank.test",
          Duration.ofHours(24),
          20,
          Duration.ofMinutes(2),
          Duration.ofSeconds(30),
          Duration.ofHours(1),
          3,
          "u",
          "a",
          "p",
          3,
          (short) 1);

  @Mock private NotificationStore store;
  @Mock private Directory directory;
  @Mock private EmailSender emailSender;

  private final SimpleMeterRegistry meters = new SimpleMeterRegistry();
  private DispatchNotificationsService service;

  @BeforeEach
  void setUp() {
    service =
        new DispatchNotificationsService(store, directory, emailSender, PROPERTIES, CLOCK, meters);
  }

  @Test
  @DisplayName("Claims with a lease, sends to the account owner, masks the number, marks sent")
  void sendsToAccountOwner() {
    UUID account = UUID.randomUUID();
    UUID owner = UUID.randomUUID();
    Notification n = moneyReceived(account, 1);
    when(store.claimDue(NOW, NOW.plus(Duration.ofMinutes(2)), 20)).thenReturn(List.of(n));
    when(directory.account(account))
        .thenReturn(Optional.of(new KnownAccount(account, "100000004821", owner, "INR")));
    when(directory.emailOf(owner)).thenReturn(Optional.of("owner@example.com"));

    assertThat(service.dispatch()).isOne();

    ArgumentCaptor<EmailMessage> email = ArgumentCaptor.forClass(EmailMessage.class);
    verify(emailSender).send(eq("owner@example.com"), email.capture());
    assertThat(email.getValue().text()).contains("•••• 4821").doesNotContain("100000004821");
    verify(store).markSent(n.id(), NOW);
    assertThat(
            meters
                .get("novabank.notifications.sent")
                .tag("kind", "MONEY_RECEIVED")
                .counter()
                .count())
        .isOne();
  }

  @Test
  @DisplayName("Unknown recipient: retried later with backoff, nothing sent")
  void unknownRecipientWaits() {
    Notification n = moneyReceived(UUID.randomUUID(), 2);
    when(store.claimDue(any(), any(), anyInt())).thenReturn(List.of(n));
    when(directory.account(any())).thenReturn(Optional.empty());

    service.dispatch();

    verify(emailSender, never()).send(any(), any());
    // Attempt 2: 30 s × 2.
    verify(store).retryLater(n.id(), NOW.plusSeconds(60), "Recipient not known yet");
  }

  @Test
  @DisplayName("Mail server down: retried; after the last attempt the notification is FAILED")
  void mailOutage() {
    UUID user = UUID.randomUUID();
    Notification early = transferSent(user, 1);
    Notification last = transferSent(user, 3);
    when(store.claimDue(any(), any(), anyInt())).thenReturn(List.of(early, last));
    when(directory.emailOf(user)).thenReturn(Optional.of("sender@example.com"));
    when(directory.account(any())).thenReturn(Optional.empty());
    doThrow(new EmailDeliveryException("Mail server refused or unreachable", null))
        .when(emailSender)
        .send(any(), any());

    service.dispatch();

    verify(store).retryLater(early.id(), NOW.plusSeconds(30), "Mail server refused or unreachable");
    verify(store).markFailed(last.id(), "Mail server refused or unreachable");
    verify(store, never()).markSent(any(), any());
    assertThat(
            meters
                .get("novabank.notifications.failed")
                .tag("kind", "TRANSFER_SENT")
                .counter()
                .count())
        .isOne();
  }

  @Test
  @DisplayName("Retry delay doubles and is capped")
  void retryDelay() {
    assertThat(PROPERTIES.retryDelay(1)).isEqualTo(Duration.ofSeconds(30));
    assertThat(PROPERTIES.retryDelay(4)).isEqualTo(Duration.ofMinutes(4));
    assertThat(PROPERTIES.retryDelay(30)).isEqualTo(Duration.ofHours(1));
  }

  private static Notification moneyReceived(UUID account, int attempts) {
    return new Notification(
        UUID.randomUUID(),
        UUID.randomUUID(),
        NotificationKind.MONEY_RECEIVED,
        null,
        account,
        Map.of("amount", "10.00", "currency", "INR", "accountId", account.toString()),
        attempts,
        NOW);
  }

  private static Notification transferSent(UUID user, int attempts) {
    return new Notification(
        UUID.randomUUID(),
        UUID.randomUUID(),
        NotificationKind.TRANSFER_SENT,
        user,
        null,
        Map.of("amount", "10.00", "currency", "INR", "accountId", UUID.randomUUID().toString()),
        attempts,
        NOW);
  }
}
