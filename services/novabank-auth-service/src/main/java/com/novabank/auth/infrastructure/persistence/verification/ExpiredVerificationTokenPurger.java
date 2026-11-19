package com.novabank.auth.infrastructure.persistence.verification;

import com.novabank.auth.application.port.persistence.EmailVerificationTokenStore;
import java.time.Clock;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Deletes verification tokens some time after they expire (1.13b). A used or expired link is
 * refused either way, so this only keeps the table small; the retention leaves expired rows around
 * for a while for support questions ("I clicked the link last week").
 *
 * <p>Safe on several instances at once: each batch deletes whatever is still there.
 */
@Component
class ExpiredVerificationTokenPurger {

  private static final Logger LOGGER =
      LoggerFactory.getLogger(ExpiredVerificationTokenPurger.class);
  static final int BATCH_SIZE = 1000;

  private final EmailVerificationTokenStore tokens;
  private final Clock clock;
  private final Duration retention;

  ExpiredVerificationTokenPurger(
      EmailVerificationTokenStore tokens,
      Clock clock,
      @Value("${verification.email.purge.retention:7d}") Duration retention) {
    this.tokens = tokens;
    this.clock = clock;
    this.retention = retention;
  }

  @Scheduled(
      fixedDelayString = "${verification.email.purge.interval:1h}",
      initialDelayString = "${verification.email.purge.initial-delay:5m}")
  void purge() {
    var cutoff = clock.instant().minus(retention);
    int deleted;
    int total = 0;
    do {
      deleted = tokens.deleteExpiredBefore(cutoff, BATCH_SIZE);
      total += deleted;
    } while (deleted == BATCH_SIZE);
    if (total > 0) {
      LOGGER.info("Deleted {} verification tokens that expired before {}", total, cutoff);
    }
  }
}
