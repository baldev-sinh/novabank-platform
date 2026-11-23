package com.novabank.payment.infrastructure.metrics;

import com.novabank.payment.domain.model.Payment;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Payment health for dashboards and alerts (7.7):
 *
 * <ul>
 *   <li>{@code novabank_payments_finished_total{type, status}}: counted after the commit, so a
 *       rolled-back transition is never counted. No failure-reason tag: reasons come from other
 *       services' replies, and an unbounded label set would grow Prometheus without limit.
 *   <li>{@code novabank_payments_in_flight}, {@code novabank_payments_needing_attention} (the
 *       sweeper flagged them after {@code max-attempts}) and {@code
 *       novabank_payments_oldest_in_flight_age_seconds}: money that hasn't arrived yet.
 * </ul>
 *
 * The gauges are measured on a schedule, never on a scrape.
 */
@Component
public class PaymentMetrics {

  private final JdbcClient jdbcClient;
  private final Clock clock;
  private final MeterRegistry meterRegistry;
  private final AtomicLong inFlight = new AtomicLong();
  private final AtomicLong needingAttention = new AtomicLong();
  private final AtomicLong oldestInFlightSeconds = new AtomicLong();

  public PaymentMetrics(JdbcClient jdbcClient, Clock clock, MeterRegistry meterRegistry) {
    this.jdbcClient = jdbcClient;
    this.clock = clock;
    this.meterRegistry = meterRegistry;
    Gauge.builder("novabank.payments.in.flight", inFlight, AtomicLong::get)
        .description("Payments not yet COMPLETED or FAILED")
        .register(meterRegistry);
    Gauge.builder("novabank.payments.needing.attention", needingAttention, AtomicLong::get)
        .description("In-flight payments the sweeper flagged for operations staff")
        .register(meterRegistry);
    Gauge.builder("novabank.payments.oldest.in.flight.age", oldestInFlightSeconds, AtomicLong::get)
        .description("Age of the oldest in-flight payment; 0 when there is none")
        .baseUnit("seconds")
        .register(meterRegistry);
  }

  /** Call inside the transaction that finishes the payment. */
  public void finished(Payment payment) {
    Counter counter =
        Counter.builder("novabank.payments.finished")
            .description("Payments that reached COMPLETED or FAILED")
            .tag("type", payment.type().name())
            .tag("status", payment.status().name())
            .register(meterRegistry);
    if (TransactionSynchronizationManager.isSynchronizationActive()) {
      TransactionSynchronizationManager.registerSynchronization(
          new TransactionSynchronization() {
            @Override
            public void afterCommit() {
              counter.increment();
            }
          });
    } else {
      counter.increment();
    }
  }

  @Scheduled(
      fixedDelayString = "${payments.metrics.interval:15s}",
      initialDelayString = "${payments.metrics.interval:15s}")
  public void measure() {
    InFlight current =
        jdbcClient
            .sql(
                """
                SELECT count(*), count(*) FILTER (WHERE needs_attention), min(created_at)
                FROM payments WHERE status NOT IN ('COMPLETED', 'FAILED')
                """)
            .query((rs, row) -> new InFlight(rs.getLong(1), rs.getLong(2), rs.getTimestamp(3)))
            .single();
    inFlight.set(current.count());
    needingAttention.set(current.needingAttention());
    oldestInFlightSeconds.set(
        current.oldest() == null
            ? 0
            : Math.max(
                0, Duration.between(current.oldest().toInstant(), clock.instant()).toSeconds()));
  }

  private record InFlight(long count, long needingAttention, Timestamp oldest) {}
}
