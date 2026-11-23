package com.novabank.audit.domain;

import java.time.Instant;
import java.util.UUID;

/**
 * One recorded event, chained to the entry before it.
 *
 * @param recordKey the Kafka key: the aggregate the event is about (may be {@code null})
 * @param payload the event exactly as received
 */
public record AuditEntry(
    long seq,
    UUID eventId,
    String eventType,
    String topic,
    String recordKey,
    Instant occurredAt,
    Instant recordedAt,
    String payload,
    byte[] prevHash,
    byte[] hash) {}
