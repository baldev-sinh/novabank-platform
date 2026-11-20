package com.novabank.account.infrastructure.messaging;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.novabank.account.application.usecase.CheckProjectionUseCase;
import com.novabank.account.application.usecase.CheckProjectionUseCase.LedgerBalance;
import com.novabank.account.application.usecase.CheckProjectionUseCase.Result;
import com.novabank.platform.messaging.InvalidMessageException;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

/**
 * Drift detection (6.2): checks each LedgerBalanceSnapshot part against the projection. Results go
 * to {@code novabank_projection_check_accounts_total{result=...}} so an alert can fire on any
 * {@code drift}; drifted account ids are logged at WARN (ids only, no customer data).
 */
@Component
class BalanceSnapshotHandler {

  private static final Logger LOGGER = LoggerFactory.getLogger(BalanceSnapshotHandler.class);
  private static final String METRIC = "novabank.projection.check.accounts";

  private final CheckProjectionUseCase check;
  private final JsonMapper jsonMapper;
  private final Counter matched;
  private final Counter drift;
  private final Counter lagging;
  private final Counter ahead;
  private final Counter unknown;

  BalanceSnapshotHandler(
      CheckProjectionUseCase check, JsonMapper jsonMapper, MeterRegistry meterRegistry) {
    this.check = check;
    this.jsonMapper = jsonMapper;
    this.matched = counter(meterRegistry, "match");
    this.drift = counter(meterRegistry, "drift");
    this.lagging = counter(meterRegistry, "lagging");
    this.ahead = counter(meterRegistry, "ahead");
    this.unknown = counter(meterRegistry, "unknown");
  }

  void handle(String json) {
    Snapshot snapshot = parse(json);
    Result result = check.check(snapshot.balances());
    matched.increment(result.matched());
    drift.increment(result.drifted().size());
    lagging.increment(result.lagging());
    ahead.increment(result.ahead());
    unknown.increment(result.unknown());
    if (!result.drifted().isEmpty()) {
      LOGGER.warn(
          "Balance projection differs from the ledger for accounts {} (snapshot {} part {})",
          result.drifted(),
          snapshot.snapshotId(),
          snapshot.part());
    }
  }

  private Snapshot parse(String json) {
    try {
      Snapshot snapshot = jsonMapper.readValue(json, Snapshot.class);
      if (snapshot.accounts() == null) {
        throw new InvalidMessageException("LedgerBalanceSnapshot has no accounts", null);
      }
      return snapshot;
    } catch (JacksonException | IllegalArgumentException ex) {
      throw new InvalidMessageException("LedgerBalanceSnapshot is not valid", ex);
    }
  }

  private static Counter counter(MeterRegistry registry, String result) {
    return Counter.builder(METRIC)
        .description("Accounts compared with a ledger balance snapshot, by outcome")
        .tag("result", result)
        .register(registry);
  }

  @JsonIgnoreProperties(ignoreUnknown = true)
  record Snapshot(UUID snapshotId, int part, List<Entry> accounts) {

    List<LedgerBalance> balances() {
      return accounts.stream()
          .map(e -> new LedgerBalance(e.accountId(), new BigDecimal(e.balance()), e.sequence()))
          .toList();
    }
  }

  @JsonIgnoreProperties(ignoreUnknown = true)
  record Entry(UUID accountId, String balance, long sequence) {}
}
