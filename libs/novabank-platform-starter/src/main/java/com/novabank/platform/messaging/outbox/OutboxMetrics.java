package com.novabank.platform.messaging.outbox;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.scheduling.annotation.Scheduled;

/**
 * The outbox backlog (7.7): {@code novabank_outbox_pending} rows and {@code
 * novabank_outbox_oldest_age_seconds}. A growing age means events have stopped leaving this service
 * (Kafka down, relay stuck), which no other metric shows. Measured on a schedule, not on each
 * scrape, so Prometheus never causes database load; the query uses the partial index on unpublished
 * rows.
 */
public class OutboxMetrics {

  private final JdbcClient jdbcClient;
  private final Clock clock;
  private final AtomicLong pending = new AtomicLong();
  private final AtomicLong oldestAgeSeconds = new AtomicLong();

  public OutboxMetrics(JdbcClient jdbcClient, Clock clock, MeterRegistry meterRegistry) {
    this.jdbcClient = jdbcClient;
    this.clock = clock;
    Gauge.builder("novabank.outbox.pending", pending, AtomicLong::get)
        .description("Outbox rows not yet published to Kafka")
        .register(meterRegistry);
    Gauge.builder("novabank.outbox.oldest.age", oldestAgeSeconds, AtomicLong::get)
        .description("Age of the oldest unpublished outbox row; 0 when there is none")
        .baseUnit("seconds")
        .register(meterRegistry);
  }

  @Scheduled(
      fixedDelayString = "${novabank.outbox.metrics.interval:15s}",
      initialDelayString = "${novabank.outbox.metrics.interval:15s}")
  public void measure() {
    Backlog backlog =
        jdbcClient
            .sql("SELECT count(*), min(created_at) FROM outbox_events WHERE published_at IS NULL")
            .query((rs, row) -> new Backlog(rs.getLong(1), rs.getTimestamp(2)))
            .single();
    pending.set(backlog.count());
    oldestAgeSeconds.set(
        backlog.oldest() == null
            ? 0
            : Math.max(
                0, Duration.between(backlog.oldest().toInstant(), clock.instant()).toSeconds()));
  }

  long pending() {
    return pending.get();
  }

  long oldestAgeSeconds() {
    return oldestAgeSeconds.get();
  }

  private record Backlog(long count, Timestamp oldest) {}
}
