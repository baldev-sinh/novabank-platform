package com.novabank.auth.infrastructure.messaging.outbox;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** One row of {@code outbox_events}; see V3 migration. */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "outbox_events")
public class OutboxEventEntity {

  @Id private UUID id;

  @Column(name = "aggregate_type", nullable = false, updatable = false)
  private String aggregateType;

  @Column(name = "aggregate_id", nullable = false, updatable = false)
  private UUID aggregateId;

  @Column(name = "event_type", nullable = false, updatable = false)
  private String eventType;

  /** Already-serialized JSON, stored as PostgreSQL jsonb. */
  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "payload", nullable = false, updatable = false)
  private String payload;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "published_at")
  private Instant publishedAt;

  static OutboxEventEntity of(
      UUID id,
      String aggregateType,
      UUID aggregateId,
      String eventType,
      String payload,
      Instant createdAt) {
    OutboxEventEntity entity = new OutboxEventEntity();
    entity.id = id;
    entity.aggregateType = aggregateType;
    entity.aggregateId = aggregateId;
    entity.eventType = eventType;
    entity.payload = payload;
    entity.createdAt = createdAt;
    return entity;
  }

  void markPublished(Instant when) {
    this.publishedAt = when;
  }
}
