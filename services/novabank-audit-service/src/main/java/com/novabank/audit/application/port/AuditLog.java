package com.novabank.audit.application.port;

import com.novabank.audit.domain.AuditEntry;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** The append-only store. */
public interface AuditLog {

  /** The end of the chain, locked until the transaction ends: appends happen one at a time. */
  ChainHead lockHead();

  /** The end of the chain as last committed, without a lock. */
  ChainHead head();

  boolean contains(UUID eventId);

  /**
   * Inserts the entry and moves the head to it; requires {@link #lockHead()} in this transaction.
   */
  void append(AuditEntry entry);

  /** Entries with {@code afterSeq < seq <= untilSeq}, oldest first. */
  List<AuditEntry> chain(long afterSeq, long untilSeq, int limit);

  /** Newest first, {@code seq < beforeSeq}; every filter is optional. */
  List<AuditEntry> search(AuditQuery query);

  record ChainHead(long seq, byte[] hash) {}

  /**
   * @param recordKey the aggregate id (the Kafka key)
   * @param from inclusive, on occurredAt
   * @param to exclusive, on occurredAt
   * @param beforeSeq the cursor from the previous page
   */
  record AuditQuery(
      String recordKey, String eventType, Instant from, Instant to, Long beforeSeq, int limit) {}
}
