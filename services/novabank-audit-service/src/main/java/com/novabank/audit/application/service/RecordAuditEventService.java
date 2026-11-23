package com.novabank.audit.application.service;

import com.novabank.audit.application.port.AuditLog;
import com.novabank.audit.application.port.AuditLog.ChainHead;
import com.novabank.audit.domain.AuditEntry;
import com.novabank.audit.domain.ChainHasher;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Appends one event to the chain, at most once per eventId. */
@Service
public class RecordAuditEventService {

  private final AuditLog auditLog;
  private final ChainHasher hasher;
  private final Clock clock;

  public RecordAuditEventService(AuditLog auditLog, ChainHasher hasher, Clock clock) {
    this.auditLog = auditLog;
    this.hasher = hasher;
    this.clock = clock;
  }

  /**
   * @return {@code false} if the event was already recorded (a redelivery)
   */
  @Transactional
  public boolean record(
      UUID eventId,
      String eventType,
      String topic,
      String recordKey,
      Instant occurredAt,
      String payload) {
    // Lock first: the duplicate check and the append then can't race another delivery.
    ChainHead head = auditLog.lockHead();
    if (auditLog.contains(eventId)) {
      return false;
    }
    long seq = head.seq() + 1;
    Instant occurred = occurredAt.truncatedTo(ChronoUnit.MILLIS);
    Instant recorded = clock.instant().truncatedTo(ChronoUnit.MILLIS);
    byte[] hash =
        hasher.hash(
            head.hash(), seq, eventId, eventType, topic, recordKey, occurred, recorded, payload);
    auditLog.append(
        new AuditEntry(
            seq,
            eventId,
            eventType,
            topic,
            recordKey,
            occurred,
            recorded,
            payload,
            head.hash(),
            hash));
    return true;
  }
}
