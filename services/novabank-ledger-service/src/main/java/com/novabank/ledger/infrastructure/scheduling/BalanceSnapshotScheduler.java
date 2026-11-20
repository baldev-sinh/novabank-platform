package com.novabank.ledger.infrastructure.scheduling;

import com.novabank.ledger.application.usecase.PublishBalanceSnapshotUseCase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

/**
 * Publishes the balance snapshot (6.2) every {@code ledger.snapshot.interval}. Every ledger
 * instance runs this; the advisory lock lets only one of them take each snapshot.
 */
@Configuration(proxyBeanMethods = false)
@EnableScheduling
@ConditionalOnBooleanProperty(name = "ledger.snapshot.enabled", matchIfMissing = true)
class BalanceSnapshotScheduler {

  private static final Logger LOGGER = LoggerFactory.getLogger(BalanceSnapshotScheduler.class);

  private final PublishBalanceSnapshotUseCase snapshot;

  BalanceSnapshotScheduler(PublishBalanceSnapshotUseCase snapshot) {
    this.snapshot = snapshot;
  }

  @Scheduled(
      fixedDelayString = "${ledger.snapshot.interval:15m}",
      initialDelayString = "${ledger.snapshot.initial-delay:2m}")
  void publishSnapshot() {
    int accounts = snapshot.publish();
    if (accounts >= 0) {
      LOGGER.info("Published balance snapshot of {} customer accounts", accounts);
    }
  }
}
