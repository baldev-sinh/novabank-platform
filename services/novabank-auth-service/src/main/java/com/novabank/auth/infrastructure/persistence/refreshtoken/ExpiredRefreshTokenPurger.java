package com.novabank.auth.infrastructure.persistence.refreshtoken;

import com.novabank.auth.application.port.persistence.RefreshTokenStore;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Deletes refresh tokens of sign-ins that ended more than a day ago (7.1). Until then they're kept
 * so a reused token is still recognised and logged as reuse rather than as an unknown token.
 */
@Component
class ExpiredRefreshTokenPurger {

  private static final Logger LOGGER = LoggerFactory.getLogger(ExpiredRefreshTokenPurger.class);
  static final int BATCH_SIZE = 1000;

  private final RefreshTokenStore tokens;
  private final Clock clock;
  private final Duration retention;

  ExpiredRefreshTokenPurger(
      RefreshTokenStore tokens,
      Clock clock,
      @Value("${security.refresh-token.purge.retention:1d}") Duration retention) {
    this.tokens = tokens;
    this.clock = clock;
    this.retention = retention;
  }

  @Scheduled(
      fixedDelayString = "${security.refresh-token.purge.interval:1h}",
      initialDelayString = "${security.refresh-token.purge.initial-delay:5m}")
  void purge() {
    Instant cutoff = clock.instant().minus(retention);
    int deleted;
    int total = 0;
    do {
      deleted = tokens.deleteExpiredBefore(cutoff, BATCH_SIZE);
      total += deleted;
    } while (deleted == BATCH_SIZE);
    if (total > 0) {
      LOGGER.info("Deleted {} refresh tokens of sign-ins that ended before {}", total, cutoff);
    }
  }
}
