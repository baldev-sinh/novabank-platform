package com.novabank.customer.infrastructure.messaging;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.novabank.customer.application.usecase.RecordRegisteredUserUseCase;
import com.novabank.platform.messaging.InvalidMessageException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.json.JsonMapper;

@ExtendWith(MockitoExtension.class)
class UserRegisteredListenerTest {

  private static final UUID EVENT_ID = UUID.randomUUID();
  private static final UUID USER_ID = UUID.randomUUID();

  @Mock private RecordRegisteredUserUseCase recordRegisteredUser;

  private UserRegisteredListener listener() {
    return new UserRegisteredListener(recordRegisteredUser, JsonMapper.builder().build());
  }

  @Test
  @DisplayName("A UserRegistered event is passed to the use case; extra payload fields are ignored")
  void handlesUserRegistered() {
    String payload =
        "{\"eventId\":\"%s\",\"userId\":\"%s\",\"email\":\"user@example.com\",\"occurredAt\":\"2026-11-16T10:00:00Z\",\"newField\":1}"
            .formatted(EVENT_ID, USER_ID);

    listener().onUserEvent(record("UserRegistered.v1", EVENT_ID.toString(), payload));

    verify(recordRegisteredUser)
        .record(EVENT_ID, USER_ID, "user@example.com", Instant.parse("2026-11-16T10:00:00Z"));
  }

  @Test
  @DisplayName("Unknown event types are skipped, even with an unreadable payload")
  void skipsUnknownEventType() {
    listener().onUserEvent(record("UserLocked.v1", EVENT_ID.toString(), "not json"));

    verifyNoInteractions(recordRegisteredUser);
  }

  @Test
  @DisplayName("Malformed JSON is an invalid event (not retried)")
  void rejectsMalformedJson() {
    assertThatThrownBy(
            () -> listener().onUserEvent(record("UserRegistered.v1", EVENT_ID.toString(), "{")))
        .isInstanceOf(InvalidMessageException.class);
  }

  @Test
  @DisplayName("A payload with missing fields is an invalid event")
  void rejectsMissingFields() {
    String payload = "{\"userId\":\"%s\"}".formatted(USER_ID);

    assertThatThrownBy(
            () -> listener().onUserEvent(record("UserRegistered.v1", EVENT_ID.toString(), payload)))
        .isInstanceOf(InvalidMessageException.class)
        .hasMessageContaining("missing fields");
  }

  @Test
  @DisplayName("A missing or malformed eventId header is an invalid event")
  void rejectsBadEventId() {
    assertThatThrownBy(() -> listener().onUserEvent(record("UserRegistered.v1", null, "{}")))
        .isInstanceOf(InvalidMessageException.class);
    assertThatThrownBy(() -> listener().onUserEvent(record("UserRegistered.v1", "abc", "{}")))
        .isInstanceOf(InvalidMessageException.class);
  }

  @Test
  @DisplayName("A tombstone (null value) is an invalid event")
  void rejectsTombstone() {
    assertThatThrownBy(
            () -> listener().onUserEvent(record("UserRegistered.v1", EVENT_ID.toString(), null)))
        .isInstanceOf(InvalidMessageException.class);
  }

  private static ConsumerRecord<String, String> record(
      String eventType, String eventId, String value) {
    ConsumerRecord<String, String> consumed =
        new ConsumerRecord<>("auth.user-events.v1", 0, 0L, USER_ID.toString(), value);
    if (eventType != null) {
      consumed.headers().add("eventType", eventType.getBytes(StandardCharsets.UTF_8));
    }
    if (eventId != null) {
      consumed.headers().add("eventId", eventId.getBytes(StandardCharsets.UTF_8));
    }
    return consumed;
  }
}
