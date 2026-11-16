package com.novabank.customer.infrastructure.messaging;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.novabank.customer.application.usecase.RecordRegisteredUserUseCase;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Header;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

/**
 * Consumes user events from the auth service. Unknown event types are skipped so the auth service
 * can add new events without breaking this consumer. Invalid events go to the dead-letter topic.
 */
@Component
public class UserRegisteredListener {

  static final String EVENT_ID_HEADER = "eventId";
  static final String EVENT_TYPE_HEADER = "eventType";
  static final String USER_REGISTERED = "UserRegistered.v1";

  private static final Logger LOGGER = LoggerFactory.getLogger(UserRegisteredListener.class);

  private final RecordRegisteredUserUseCase recordRegisteredUser;
  private final JsonMapper jsonMapper;

  public UserRegisteredListener(
      RecordRegisteredUserUseCase recordRegisteredUser, JsonMapper jsonMapper) {
    this.recordRegisteredUser = recordRegisteredUser;
    this.jsonMapper = jsonMapper;
  }

  @KafkaListener(topics = "${events.user-events-topic}")
  public void onUserEvent(ConsumerRecord<String, String> consumed) {
    String eventType = header(consumed, EVENT_TYPE_HEADER);
    if (!USER_REGISTERED.equals(eventType)) {
      LOGGER.debug("Skipping event type {} at offset {}", eventType, consumed.offset());
      return;
    }

    UUID eventId = parseUuid(header(consumed, EVENT_ID_HEADER), "eventId header");
    UserRegisteredPayload payload = parse(consumed.value());

    boolean applied =
        recordRegisteredUser.record(
            eventId, payload.userId(), payload.email(), payload.occurredAt());
    // Ids only: no email in logs.
    LOGGER.info(
        "{} event {} for user {}",
        applied ? "Applied" : "Skipped duplicate",
        eventId,
        payload.userId());
  }

  private UserRegisteredPayload parse(String json) {
    try {
      UserRegisteredPayload payload = jsonMapper.readValue(json, UserRegisteredPayload.class);
      if (payload.userId() == null || payload.email() == null || payload.occurredAt() == null) {
        throw new InvalidEventException("UserRegistered payload is missing fields", null);
      }
      return payload;
    } catch (JacksonException | IllegalArgumentException ex) {
      throw new InvalidEventException("UserRegistered payload is not valid JSON", ex);
    }
  }

  private static String header(ConsumerRecord<String, String> consumed, String name) {
    Header header = consumed.headers().lastHeader(name);
    return header == null ? null : new String(header.value(), StandardCharsets.UTF_8);
  }

  private static UUID parseUuid(String value, String what) {
    try {
      return UUID.fromString(value);
    } catch (IllegalArgumentException | NullPointerException ex) {
      throw new InvalidEventException("Missing or invalid " + what, ex);
    }
  }

  /** The part of the auth service's event this service needs; other fields are ignored. */
  @JsonIgnoreProperties(ignoreUnknown = true)
  record UserRegisteredPayload(UUID userId, String email, Instant occurredAt) {}
}
