package com.novabank.platform.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class MessageHeadersTest {

  @Test
  @DisplayName("Reads eventId and eventType; the last header of a name wins")
  void readsHeaders() {
    UUID id = UUID.randomUUID();
    ConsumerRecord<String, String> consumed = record();
    consumed
        .headers()
        .add(MessageHeaders.EVENT_ID, MessageHeaders.bytes(UUID.randomUUID().toString()));
    consumed.headers().add(MessageHeaders.EVENT_ID, MessageHeaders.bytes(id.toString()));
    consumed.headers().add(MessageHeaders.EVENT_TYPE, MessageHeaders.bytes("Thing.v1"));

    assertThat(MessageHeaders.eventId(consumed)).isEqualTo(id);
    assertThat(MessageHeaders.eventType(consumed)).isEqualTo("Thing.v1");
  }

  @Test
  @DisplayName(
      "A missing header reads as null; a missing or malformed eventId is an invalid message")
  void missingOrMalformedHeaders() {
    ConsumerRecord<String, String> empty = record();
    assertThat(MessageHeaders.eventType(empty)).isNull();
    assertThatThrownBy(() -> MessageHeaders.eventId(empty))
        .isInstanceOf(InvalidMessageException.class)
        .hasMessageContaining("Missing");

    ConsumerRecord<String, String> malformed = record();
    malformed.headers().add(MessageHeaders.EVENT_ID, MessageHeaders.bytes("not-a-uuid"));
    assertThatThrownBy(() -> MessageHeaders.eventId(malformed))
        .isInstanceOf(InvalidMessageException.class)
        .hasMessageContaining("Invalid");
  }

  private static ConsumerRecord<String, String> record() {
    return new ConsumerRecord<>("topic", 0, 0L, "key", "value");
  }
}
