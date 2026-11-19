package com.novabank.platform.idempotency;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.json.JsonMapper;

/** Idempotency-Key semantics against real PostgreSQL. */
class IdempotentRequestsIT {

  private final IdempotentRequests requests =
      new IdempotentRequests(
          PostgresTestDatabase.jdbcClient(), JsonMapper.builder().build(), Clock.systemUTC());
  private final TransactionTemplate transactions = PostgresTestDatabase.transactions();
  private final UUID owner = UUID.randomUUID();

  record Created(String id, String type) {}

  @Test
  @DisplayName("The first request runs the action; a retry with the same key replays its result")
  void retryReplaysTheStoredResult() {
    AtomicInteger runs = new AtomicInteger();

    IdempotentRequests.Result<Created> first = execute("key-1", "CURRENT|INR", runs);
    IdempotentRequests.Result<Created> retry = execute("key-1", "CURRENT|INR", runs);

    assertThat(first.replayed()).isFalse();
    assertThat(retry.replayed()).isTrue();
    assertThat(retry.value()).isEqualTo(first.value());
    assertThat(runs).hasValue(1);
  }

  @Test
  @DisplayName("Keys are per caller: another owner may use the same key for its own request")
  void keysArePerOwner() {
    AtomicInteger runs = new AtomicInteger();
    execute("shared-key", "CURRENT|INR", runs);

    IdempotentRequests.Result<Created> other =
        transactions.execute(status ->
            requests.execute(
                UUID.randomUUID(),
                "shared-key",
                "CURRENT|INR",
                Created.class,
                () -> new Created(UUID.randomUUID().toString(), "CURRENT")));

    assertThat(other.replayed()).isFalse();
  }

  @Test
  @DisplayName("The same key with a different request is rejected")
  void reusedKeyWithDifferentRequestIsRejected() {
    execute("key-2", "CURRENT|INR", new AtomicInteger());

    assertThatThrownBy(() -> execute("key-2", "SAVINGS|INR", new AtomicInteger()))
        .isInstanceOf(IdempotencyKeyReusedException.class);
  }

  @Test
  @DisplayName("A failed action rolls the claim back, so the same key can be retried")
  void failedActionCanBeRetried() {
    assertThatThrownBy(() ->
            transactions.execute(status ->
                requests.execute(
                    owner,
                    "key-3",
                    "CURRENT|INR",
                    Created.class,
                    () -> {
                      throw new IllegalStateException("downstream failed");
                    })))
        .isInstanceOf(IllegalStateException.class);

    IdempotentRequests.Result<Created> retry = execute("key-3", "CURRENT|INR", new AtomicInteger());

    assertThat(retry.replayed()).isFalse();
  }

  @Test
  @DisplayName("Concurrent duplicates: the action runs once, every caller gets the same result")
  void concurrentDuplicatesRunOnce() throws Exception {
    AtomicInteger runs = new AtomicInteger();
    CountDownLatch start = new CountDownLatch(1);
    Callable<IdempotentRequests.Result<Created>> request =
        () -> {
          start.await();
          return execute("key-4", "CURRENT|INR", runs);
        };

    List<IdempotentRequests.Result<Created>> results = new ArrayList<>();
    try (ExecutorService pool = Executors.newFixedThreadPool(6)) {
      List<Future<IdempotentRequests.Result<Created>>> futures = new ArrayList<>();
      for (int i = 0; i < 6; i++) {
        futures.add(pool.submit(request));
      }
      start.countDown();
      for (Future<IdempotentRequests.Result<Created>> future : futures) {
        results.add(future.get(30, TimeUnit.SECONDS));
      }
    }

    assertThat(runs).hasValue(1);
    assertThat(results).filteredOn(result -> !result.replayed()).hasSize(1);
    assertThat(results)
        .extracting(IdempotentRequests.Result::value)
        .containsOnly(results.getFirst().value());
  }

  private IdempotentRequests.Result<Created> execute(
      String key, String fingerprint, AtomicInteger runs) {
    return transactions.execute(status ->
        requests.execute(
            owner,
            key,
            fingerprint,
            Created.class,
            () -> {
              runs.incrementAndGet();
              return new Created(UUID.randomUUID().toString(), fingerprint);
            }));
  }
}
