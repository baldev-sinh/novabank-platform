package com.novabank.audit.infrastructure.messaging;

import com.novabank.audit.application.service.RecordAuditEventService;
import com.novabank.audit.infrastructure.configuration.AuditProperties;
import com.novabank.platform.messaging.InvalidMessageException;
import com.novabank.platform.messaging.MessageHeaders;
import java.time.Instant;
import java.util.UUID;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Records every message on the business event topics, whatever its type: the audit log must not
 * depend on knowing each service's events. The payload is kept as received (never parsed), and
 * nothing from it is logged.
 */
@Component
public class AuditEventsListener {

  private static final Logger LOGGER = LoggerFactory.getLogger(AuditEventsListener.class);

  private final RecordAuditEventService recordEvent;
  private final AuditProperties properties;

  public AuditEventsListener(RecordAuditEventService recordEvent, AuditProperties properties) {
    this.recordEvent = recordEvent;
    this.properties = properties;
  }

  /** The topics come from {@code audit.topics}; {@code __listener} is this bean (Spring Kafka). */
  public String[] topics() {
    return properties.topics().toArray(String[]::new);
  }

  @KafkaListener(topics = "#{__listener.topics()}")
  public void onEvent(ConsumerRecord<String, String> consumed) {
    String eventType = MessageHeaders.eventType(consumed);
    if (eventType == null || eventType.isBlank()) {
      throw new InvalidMessageException("Missing eventType header", null);
    }
    if (properties.ignoredEventTypes().contains(eventType)) {
      return;
    }
    UUID eventId = MessageHeaders.eventId(consumed);
    if (consumed.value() == null) {
      throw new InvalidMessageException("Empty payload", null);
    }

    boolean recorded =
        recordEvent.record(
            eventId,
            eventType,
            consumed.topic(),
            consumed.key(),
            Instant.ofEpochMilli(consumed.timestamp()),
            consumed.value());
    LOGGER.debug(
        "{} {} {} from {}",
        recorded ? "Recorded" : "Skipped duplicate",
        eventType,
        eventId,
        consumed.topic());
  }
}
