package com.novabank.platform.messaging.outbox;

import static org.assertj.core.api.Assertions.assertThat;

import com.novabank.platform.support.PostgresTestDatabase;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * The backlog gauges against a real table. The table is shared with OutboxRelayIT, so the
 * assertions are lower bounds plus an exact change, never absolute counts.
 */
class OutboxMetricsIT {

  private final JdbcClient jdbc = PostgresTestDatabase.jdbcClient();

  @Test
  @DisplayName("Pending rows and the oldest one's age are published as gauges")
  void measuresBacklog() {
    Instant now = Instant.parse("2030-01-01T12:00:00Z");
    SimpleMeterRegistry registry = new SimpleMeterRegistry();
    OutboxMetrics metrics = new OutboxMetrics(jdbc, Clock.fixed(now, ZoneOffset.UTC), registry);
    metrics.measure();
    long before = metrics.pending();

    UUID stuck = UUID.randomUUID();
    jdbc.sql(
            """
            INSERT INTO outbox_events (id, topic, aggregate_type, aggregate_id, event_type, payload,
                created_at)
            VALUES (:id, 'metrics-it', 'Test', :id, 'Test.v1', '{}'::jsonb, :createdAt)
            """)
        .param("id", stuck)
        .param("createdAt", Timestamp.from(now.minusSeconds(3600)))
        .update();
    metrics.measure();

    assertThat(metrics.pending()).isEqualTo(before + 1);
    assertThat(metrics.oldestAgeSeconds()).isGreaterThanOrEqualTo(3600);
    assertThat(registry.get("novabank.outbox.pending").gauge().value())
        .isEqualTo(metrics.pending());
    assertThat(registry.get("novabank.outbox.oldest.age").gauge().value())
        .isEqualTo(metrics.oldestAgeSeconds());

    jdbc.sql("UPDATE outbox_events SET published_at = :now WHERE id = :id")
        .param("now", Timestamp.from(now))
        .param("id", stuck)
        .update();
    metrics.measure();
    assertThat(metrics.pending()).isEqualTo(before);
  }
}
