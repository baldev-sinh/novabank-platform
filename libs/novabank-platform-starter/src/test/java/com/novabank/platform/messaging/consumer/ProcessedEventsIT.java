package com.novabank.platform.messaging.consumer;

import static org.assertj.core.api.Assertions.assertThat;

import com.novabank.platform.support.PostgresTestDatabase;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionTemplate;

/** Duplicate detection for idempotent consumers against real PostgreSQL. */
class ProcessedEventsIT {

  private final ProcessedEvents processed =
      new ProcessedEvents(PostgresTestDatabase.jdbcClient(), Clock.systemUTC());
  private final TransactionTemplate transactions = PostgresTestDatabase.transactions();

  @Test
  @DisplayName("A message is new once; every redelivery is reported as a duplicate")
  void redeliveryIsADuplicate() {
    UUID eventId = UUID.randomUUID();

    assertThat(mark(eventId)).isTrue();
    assertThat(mark(eventId)).isFalse();
    assertThat(mark(eventId)).isFalse();
  }

  @Test
  @DisplayName("If the effect rolls back, so does the record: the redelivery is processed again")
  void rolledBackEffectIsNotRecorded() {
    UUID eventId = UUID.randomUUID();

    transactions.executeWithoutResult(status -> {
      processed.markProcessed(eventId, "Test.v1");
      status.setRollbackOnly();
    });

    assertThat(mark(eventId)).isTrue();
  }

  @Test
  @DisplayName("Concurrent deliveries of one message: exactly one wins, none throws")
  void concurrentDeliveriesHaveOneWinner() throws Exception {
    UUID eventId = UUID.randomUUID();
    CountDownLatch start = new CountDownLatch(1);
    Callable<Boolean> delivery =
        () -> {
          start.await();
          return mark(eventId);
        };

    List<Boolean> results = new ArrayList<>();
    try (ExecutorService pool = Executors.newFixedThreadPool(8)) {
      List<Future<Boolean>> futures = new ArrayList<>();
      for (int i = 0; i < 8; i++) {
        futures.add(pool.submit(delivery));
      }
      start.countDown();
      for (Future<Boolean> future : futures) {
        results.add(future.get(30, TimeUnit.SECONDS));
      }
    }

    assertThat(results).filteredOn(Boolean::booleanValue).hasSize(1);
  }

  private boolean mark(UUID eventId) {
    return Boolean.TRUE.equals(
        transactions.execute(status -> processed.markProcessed(eventId, "Test.v1")));
  }
}
