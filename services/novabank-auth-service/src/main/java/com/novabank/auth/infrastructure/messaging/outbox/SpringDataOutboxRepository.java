package com.novabank.auth.infrastructure.messaging.outbox;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface SpringDataOutboxRepository extends JpaRepository<OutboxEventEntity, UUID> {

  /**
   * Oldest unpublished events, locked for this transaction. {@code SKIP LOCKED} lets several
   * service instances relay in parallel without ever picking the same row.
   */
  @Query(
      value =
          """
          SELECT * FROM outbox_events
          WHERE published_at IS NULL
          ORDER BY created_at
          LIMIT :limit
          FOR UPDATE SKIP LOCKED
          """,
      nativeQuery = true)
  List<OutboxEventEntity> lockNextBatch(@Param("limit") int limit);
}
