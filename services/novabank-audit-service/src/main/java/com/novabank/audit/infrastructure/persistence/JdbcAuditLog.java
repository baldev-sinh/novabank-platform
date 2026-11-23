package com.novabank.audit.infrastructure.persistence;

import com.novabank.audit.application.port.AuditLog;
import com.novabank.audit.domain.AuditEntry;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class JdbcAuditLog implements AuditLog {

  private static final String COLUMNS =
      "seq, event_id, event_type, topic, record_key, occurred_at, recorded_at, payload,"
          + " prev_hash, hash";

  private final JdbcClient jdbc;

  public JdbcAuditLog(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  @Transactional(propagation = Propagation.MANDATORY)
  public ChainHead lockHead() {
    return jdbc.sql("SELECT last_seq, last_hash FROM audit_chain_head WHERE id = 1 FOR UPDATE")
        .query((rs, row) -> new ChainHead(rs.getLong(1), rs.getBytes(2)))
        .single();
  }

  @Override
  public ChainHead head() {
    return jdbc.sql("SELECT last_seq, last_hash FROM audit_chain_head WHERE id = 1")
        .query((rs, row) -> new ChainHead(rs.getLong(1), rs.getBytes(2)))
        .single();
  }

  @Override
  public boolean contains(UUID eventId) {
    return jdbc.sql("SELECT EXISTS (SELECT 1 FROM audit_entries WHERE event_id = :id)")
        .param("id", eventId)
        .query(Boolean.class)
        .single();
  }

  @Override
  @Transactional(propagation = Propagation.MANDATORY)
  public void append(AuditEntry entry) {
    jdbc.sql(
            """
            INSERT INTO audit_entries (%s)
            VALUES (:seq, :eventId, :eventType, :topic, :recordKey, :occurredAt, :recordedAt,
                    :payload, :prevHash, :hash)
            """
                .formatted(COLUMNS))
        .param("seq", entry.seq())
        .param("eventId", entry.eventId())
        .param("eventType", entry.eventType())
        .param("topic", entry.topic())
        .param("recordKey", entry.recordKey())
        .param("occurredAt", Timestamp.from(entry.occurredAt()))
        .param("recordedAt", Timestamp.from(entry.recordedAt()))
        .param("payload", entry.payload())
        .param("prevHash", entry.prevHash())
        .param("hash", entry.hash())
        .update();
    jdbc.sql("UPDATE audit_chain_head SET last_seq = :seq, last_hash = :hash WHERE id = 1")
        .param("seq", entry.seq())
        .param("hash", entry.hash())
        .update();
  }

  @Override
  public List<AuditEntry> chain(long afterSeq, long untilSeq, int limit) {
    return jdbc.sql(
            "SELECT %s FROM audit_entries WHERE seq > :after AND seq <= :until ORDER BY seq LIMIT :limit"
                .formatted(COLUMNS))
        .param("after", afterSeq)
        .param("until", untilSeq)
        .param("limit", limit)
        .query(JdbcAuditLog::entry)
        .list();
  }

  @Override
  public List<AuditEntry> search(AuditQuery query) {
    List<String> where = new ArrayList<>();
    Map<String, Object> params = new HashMap<>();
    params.put("limit", query.limit());
    if (query.recordKey() != null) {
      where.add("record_key = :recordKey");
      params.put("recordKey", query.recordKey());
    }
    if (query.eventType() != null) {
      where.add("event_type = :eventType");
      params.put("eventType", query.eventType());
    }
    if (query.from() != null) {
      where.add("occurred_at >= :from");
      params.put("from", Timestamp.from(query.from()));
    }
    if (query.to() != null) {
      where.add("occurred_at < :to");
      params.put("to", Timestamp.from(query.to()));
    }
    if (query.beforeSeq() != null) {
      where.add("seq < :beforeSeq");
      params.put("beforeSeq", query.beforeSeq());
    }
    // Only fixed fragments are joined; every value is a bind parameter.
    String sql =
        "SELECT %s FROM audit_entries %s ORDER BY seq DESC LIMIT :limit"
            .formatted(COLUMNS, where.isEmpty() ? "" : "WHERE " + String.join(" AND ", where));
    return jdbc.sql(sql).params(params).query(JdbcAuditLog::entry).list();
  }

  private static AuditEntry entry(ResultSet rs, int row) throws SQLException {
    return new AuditEntry(
        rs.getLong("seq"),
        rs.getObject("event_id", UUID.class),
        rs.getString("event_type"),
        rs.getString("topic"),
        rs.getString("record_key"),
        rs.getTimestamp("occurred_at").toInstant(),
        rs.getTimestamp("recorded_at").toInstant(),
        rs.getString("payload"),
        rs.getBytes("prev_hash"),
        rs.getBytes("hash"));
  }
}
