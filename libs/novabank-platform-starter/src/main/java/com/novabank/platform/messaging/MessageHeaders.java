package com.novabank.platform.messaging;

import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Header;

/** Kafka headers on every NovaBank message (events and commands), written by the outbox relay. */
public final class MessageHeaders {

  /** Unique per message; consumers deduplicate on it. */
  public static final String EVENT_ID = "eventId";

  /** Versioned type name, e.g. {@code UserRegistered.v1}; lets consumers skip unknown types. */
  public static final String EVENT_TYPE = "eventType";

  private MessageHeaders() {}

  /** The header as UTF-8 text, or {@code null} if absent. */
  public static String header(ConsumerRecord<?, ?> consumed, String name) {
    Header header = consumed.headers().lastHeader(name);
    return header == null ? null : new String(header.value(), StandardCharsets.UTF_8);
  }

  public static String eventType(ConsumerRecord<?, ?> consumed) {
    return header(consumed, EVENT_TYPE);
  }

  /**
   * @throws InvalidMessageException if the header is missing or not a UUID
   */
  public static UUID eventId(ConsumerRecord<?, ?> consumed) {
    String value = header(consumed, EVENT_ID);
    if (value == null) {
      throw new InvalidMessageException("Missing eventId header", null);
    }
    try {
      return UUID.fromString(value);
    } catch (IllegalArgumentException ex) {
      throw new InvalidMessageException("Invalid eventId header", ex);
    }
  }

  public static byte[] bytes(String value) {
    return value.getBytes(StandardCharsets.UTF_8);
  }
}
