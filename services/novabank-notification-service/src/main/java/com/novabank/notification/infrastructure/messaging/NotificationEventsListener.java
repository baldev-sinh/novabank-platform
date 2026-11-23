package com.novabank.notification.infrastructure.messaging;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.novabank.notification.application.service.RecordEventsService;
import com.novabank.notification.application.service.RecordEventsService.PaymentFinished;
import com.novabank.platform.messaging.InvalidMessageException;
import com.novabank.platform.messaging.MessageHeaders;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

/**
 * User, account and payment events. Other types on these topics (saga replies, role changes) are
 * skipped, so producers can add events without breaking this consumer. Unreadable events go to this
 * service's dead-letter topics. Logs carry ids, never emails.
 */
@Component
public class NotificationEventsListener {

  private static final Logger LOGGER = LoggerFactory.getLogger(NotificationEventsListener.class);
  private static final Set<String> PAYMENT_TYPES =
      Set.of("PaymentCompleted.v1", "PaymentFailed.v1");

  private final RecordEventsService recordEvents;
  private final JsonMapper jsonMapper;

  public NotificationEventsListener(RecordEventsService recordEvents, JsonMapper jsonMapper) {
    this.recordEvents = recordEvents;
    this.jsonMapper = jsonMapper;
  }

  @KafkaListener(topics = "${notifications.user-events-topic}")
  public void onUserEvent(ConsumerRecord<String, String> consumed) {
    if (!"UserRegistered.v1".equals(MessageHeaders.eventType(consumed))) {
      return;
    }
    UserRegistered event = parse(consumed, UserRegistered.class);
    require(
        event.userId() != null && event.email() != null && event.occurredAt() != null, consumed);
    applied(
        recordEvents.userRegistered(
            MessageHeaders.eventId(consumed), event.userId(), event.email(), event.occurredAt()),
        consumed);
  }

  @KafkaListener(topics = "${notifications.account-events-topic}")
  public void onAccountEvent(ConsumerRecord<String, String> consumed) {
    String type = MessageHeaders.eventType(consumed);
    boolean opened = "AccountOpened.v1".equals(type);
    if (!opened && !"AccountActivated.v1".equals(type)) {
      return;
    }
    AccountEvent event = parse(consumed, AccountEvent.class);
    require(
        event.accountId() != null
            && event.accountNumber() != null
            && event.ownerUserId() != null
            && event.currency() != null,
        consumed);
    UUID eventId = MessageHeaders.eventId(consumed);
    applied(
        opened
            ? recordEvents.accountOpened(
                eventId,
                event.accountId(),
                event.accountNumber(),
                event.ownerUserId(),
                event.currency())
            : recordEvents.accountActivated(
                eventId,
                event.accountId(),
                event.accountNumber(),
                event.ownerUserId(),
                event.currency(),
                Instant.ofEpochMilli(consumed.timestamp())),
        consumed);
  }

  @KafkaListener(topics = "${notifications.payment-events-topic}")
  public void onPaymentEvent(ConsumerRecord<String, String> consumed) {
    String type = MessageHeaders.eventType(consumed);
    if (!PAYMENT_TYPES.contains(type)) {
      return;
    }
    PaymentEvent event = parse(consumed, PaymentEvent.class);
    require(
        event.type() != null
            && event.toAccountId() != null
            && event.amount() != null
            && event.currency() != null
            && ("DEPOSIT".equals(event.type()) || event.requestedBy() != null),
        consumed);
    applied(
        recordEvents.paymentFinished(
            MessageHeaders.eventId(consumed),
            type,
            new PaymentFinished(
                event.type(),
                "PaymentCompleted.v1".equals(type),
                event.requestedBy(),
                event.toAccountId(),
                event.amount(),
                event.currency(),
                event.failureReason(),
                Instant.ofEpochMilli(consumed.timestamp()))),
        consumed);
  }

  private <T> T parse(ConsumerRecord<String, String> consumed, Class<T> type) {
    if (consumed.value() == null) {
      throw new InvalidMessageException("Empty payload", null);
    }
    try {
      return jsonMapper.readValue(consumed.value(), type);
    } catch (JacksonException | IllegalArgumentException ex) {
      throw new InvalidMessageException(type.getSimpleName() + " payload is not valid JSON", ex);
    }
  }

  private static void require(boolean valid, ConsumerRecord<String, String> consumed) {
    if (!valid) {
      throw new InvalidMessageException(
          MessageHeaders.eventType(consumed) + " payload is missing fields", null);
    }
  }

  private static void applied(boolean applied, ConsumerRecord<String, String> consumed) {
    LOGGER.debug(
        "{} {} at {}-{}@{}",
        applied ? "Applied" : "Skipped duplicate",
        MessageHeaders.eventType(consumed),
        consumed.topic(),
        consumed.partition(),
        consumed.offset());
  }

  @JsonIgnoreProperties(ignoreUnknown = true)
  record UserRegistered(UUID userId, String email, Instant occurredAt) {}

  @JsonIgnoreProperties(ignoreUnknown = true)
  record AccountEvent(UUID accountId, String accountNumber, UUID ownerUserId, String currency) {}

  @JsonIgnoreProperties(ignoreUnknown = true)
  record PaymentEvent(
      String type,
      UUID requestedBy,
      UUID toAccountId,
      String amount,
      String currency,
      String failureReason) {}
}
