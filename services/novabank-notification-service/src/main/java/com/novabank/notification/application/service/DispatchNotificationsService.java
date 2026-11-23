package com.novabank.notification.application.service;

import com.novabank.notification.application.port.Directory;
import com.novabank.notification.application.port.Directory.KnownAccount;
import com.novabank.notification.application.port.EmailSender;
import com.novabank.notification.application.port.EmailSender.EmailDeliveryException;
import com.novabank.notification.application.port.NotificationStore;
import com.novabank.notification.domain.EmailMessage;
import com.novabank.notification.domain.Notification;
import com.novabank.notification.domain.NotificationKind;
import com.novabank.notification.domain.NotificationTemplates;
import com.novabank.notification.infrastructure.configuration.NotificationProperties;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * Sends due notifications. Claiming happens in a short transaction; the SMTP call happens outside
 * any transaction (Decision AI), so a slow mail server never holds a database connection. A
 * recipient not known yet (the user or account event hasn't arrived) is retried like a failed send.
 * Logs carry ids only, never addresses.
 */
@Service
public class DispatchNotificationsService {

  private static final Logger LOGGER = LoggerFactory.getLogger(DispatchNotificationsService.class);

  private final NotificationStore notifications;
  private final Directory directory;
  private final EmailSender emailSender;
  private final NotificationProperties properties;
  private final Clock clock;
  private final MeterRegistry meters;

  public DispatchNotificationsService(
      NotificationStore notifications,
      Directory directory,
      EmailSender emailSender,
      NotificationProperties properties,
      Clock clock,
      MeterRegistry meters) {
    this.notifications = notifications;
    this.directory = directory;
    this.emailSender = emailSender;
    this.properties = properties;
    this.clock = clock;
    this.meters = meters;
    // At 0 from the start: an alert on increase() would otherwise miss the very first failure.
    for (NotificationKind kind : NotificationKind.values()) {
      counter("novabank.notifications.sent", kind);
      counter("novabank.notifications.failed", kind);
    }
  }

  /**
   * @return how many notifications this run handled (sent, rescheduled or failed)
   */
  @Scheduled(fixedDelayString = "${notifications.dispatch-interval:2s}")
  public int dispatch() {
    Instant now = clock.instant();
    List<Notification> due =
        notifications.claimDue(now, now.plus(properties.lease()), properties.batchSize());
    due.forEach(this::deliver);
    return due.size();
  }

  private void deliver(Notification notification) {
    Optional<String> to = recipient(notification);
    if (to.isEmpty()) {
      giveUpOrRetry(notification, "Recipient not known yet");
      return;
    }
    EmailMessage message =
        NotificationTemplates.render(
            notification.kind(), notification.details(), accountNumber(notification));
    try {
      emailSender.send(to.get(), message);
    } catch (EmailDeliveryException ex) {
      giveUpOrRetry(notification, ex.getMessage());
      return;
    }
    notifications.markSent(notification.id(), clock.instant());
    counter("novabank.notifications.sent", notification).increment();
    LOGGER.info("Sent {} notification {}", notification.kind(), notification.id());
  }

  private void giveUpOrRetry(Notification notification, String reason) {
    if (notification.attempts() >= properties.maxAttempts()) {
      notifications.markFailed(notification.id(), reason);
      counter("novabank.notifications.failed", notification).increment();
      LOGGER.error(
          "Gave up on {} notification {} after {} attempts: {}",
          notification.kind(),
          notification.id(),
          notification.attempts(),
          reason);
      return;
    }
    notifications.retryLater(
        notification.id(),
        clock.instant().plus(properties.retryDelay(notification.attempts())),
        reason);
    LOGGER.warn(
        "Will retry {} notification {} (attempt {}): {}",
        notification.kind(),
        notification.id(),
        notification.attempts(),
        reason);
  }

  private Optional<String> recipient(Notification notification) {
    UUID userId =
        notification.recipientUserId() != null
            ? notification.recipientUserId()
            : directory
                .account(notification.recipientAccountId())
                .map(KnownAccount::ownerUserId)
                .orElse(null);
    return userId == null ? Optional.empty() : directory.emailOf(userId);
  }

  /** The account the email is about; shown masked. */
  private String accountNumber(Notification notification) {
    String number = notification.details().get(RecordEventsService.ACCOUNT_NUMBER);
    if (number != null) {
      return number;
    }
    String accountId = notification.details().get(RecordEventsService.ACCOUNT_ID);
    return accountId == null
        ? null
        : directory
            .account(UUID.fromString(accountId))
            .map(KnownAccount::accountNumber)
            .orElse(null);
  }

  private Counter counter(String name, Notification notification) {
    return counter(name, notification.kind());
  }

  private Counter counter(String name, NotificationKind kind) {
    return Counter.builder(name).tag("kind", kind.name()).register(meters);
  }
}
