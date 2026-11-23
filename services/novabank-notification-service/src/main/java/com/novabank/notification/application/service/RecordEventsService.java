package com.novabank.notification.application.service;

import static com.novabank.notification.domain.NotificationTemplates.AMOUNT;
import static com.novabank.notification.domain.NotificationTemplates.CURRENCY;
import static com.novabank.notification.domain.NotificationTemplates.FAILURE_REASON;

import com.novabank.notification.application.port.Directory;
import com.novabank.notification.application.port.NotificationStore;
import com.novabank.notification.domain.Notification;
import com.novabank.notification.domain.NotificationKind;
import com.novabank.notification.infrastructure.configuration.NotificationProperties;
import com.novabank.platform.messaging.consumer.ProcessedEvents;
import java.time.Clock;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Applies each consumed event once: "processed" and its effect (projection row or notification
 * rows) commit together. Nothing is sent here; the dispatcher sends (Decision AI).
 */
@Service
public class RecordEventsService {

  /** Keys of a notification's details besides the template's. */
  public static final String ACCOUNT_ID = "accountId";

  public static final String ACCOUNT_NUMBER = "accountNumber";

  private final ProcessedEvents processedEvents;
  private final Directory directory;
  private final NotificationStore notifications;
  private final NotificationProperties properties;
  private final Clock clock;

  public RecordEventsService(
      ProcessedEvents processedEvents,
      Directory directory,
      NotificationStore notifications,
      NotificationProperties properties,
      Clock clock) {
    this.processedEvents = processedEvents;
    this.directory = directory;
    this.notifications = notifications;
    this.properties = properties;
    this.clock = clock;
  }

  @Transactional
  public boolean userRegistered(UUID eventId, UUID userId, String email, Instant registeredAt) {
    if (!processedEvents.markProcessed(eventId, "UserRegistered.v1")) {
      return false;
    }
    directory.saveUser(userId, email, registeredAt);
    return true;
  }

  @Transactional
  public boolean accountOpened(
      UUID eventId, UUID accountId, String accountNumber, UUID ownerUserId, String currency) {
    if (!processedEvents.markProcessed(eventId, "AccountOpened.v1")) {
      return false;
    }
    directory.saveAccount(accountId, accountNumber, ownerUserId, currency);
    return true;
  }

  @Transactional
  public boolean accountActivated(
      UUID eventId,
      UUID accountId,
      String accountNumber,
      UUID ownerUserId,
      String currency,
      Instant occurredAt) {
    if (!processedEvents.markProcessed(eventId, "AccountActivated.v1")) {
      return false;
    }
    // Also a projection update: AccountActivated carries everything AccountOpened does here.
    directory.saveAccount(accountId, accountNumber, ownerUserId, currency);
    if (isRecent(occurredAt)) {
      notifications.add(
          Notification.toUser(
              eventId,
              NotificationKind.ACCOUNT_READY,
              ownerUserId,
              Map.of(ACCOUNT_NUMBER, accountNumber, CURRENCY, currency),
              clock.instant()));
    }
    return true;
  }

  /**
   * Transfer completed: the sender and the beneficiary's owner. Transfer failed: the sender.
   * Deposit completed: the credited account's owner (staff requested it, so nobody else).
   */
  @Transactional
  public boolean paymentFinished(UUID eventId, String eventType, PaymentFinished payment) {
    if (!processedEvents.markProcessed(eventId, eventType)) {
      return false;
    }
    if (!isRecent(payment.occurredAt())) {
      return true;
    }
    boolean transfer = "TRANSFER".equals(payment.type());
    Map<String, String> details = new HashMap<>();
    details.put(AMOUNT, payment.amount());
    details.put(CURRENCY, payment.currency());
    details.put(ACCOUNT_ID, payment.toAccountId().toString());
    Instant now = clock.instant();
    if (payment.completed()) {
      if (transfer) {
        notifications.add(
            Notification.toUser(
                eventId, NotificationKind.TRANSFER_SENT, payment.requestedBy(), details, now));
      }
      notifications.add(
          Notification.toAccountOwner(
              eventId, NotificationKind.MONEY_RECEIVED, payment.toAccountId(), details, now));
    } else if (transfer) {
      if (payment.failureReason() != null) {
        details.put(FAILURE_REASON, payment.failureReason());
      }
      notifications.add(
          Notification.toUser(
              eventId, NotificationKind.TRANSFER_FAILED, payment.requestedBy(), details, now));
    }
    return true;
  }

  private boolean isRecent(Instant occurredAt) {
    return !occurredAt.isBefore(clock.instant().minus(properties.maxEventAge()));
  }

  /** The fields of PaymentCompleted.v1 / PaymentFailed.v1 this service uses. */
  public record PaymentFinished(
      String type,
      boolean completed,
      UUID requestedBy,
      UUID toAccountId,
      String amount,
      String currency,
      String failureReason,
      Instant occurredAt) {}
}
