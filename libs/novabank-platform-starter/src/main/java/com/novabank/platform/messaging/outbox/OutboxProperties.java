package com.novabank.platform.messaging.outbox;

import jakarta.validation.constraints.Min;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * @param enabled turns the outbox on; the service must then own the {@code outbox_events} table
 * @param batchSize max messages published per relay run
 * @param sendTimeout how long to wait for Kafka to acknowledge one message; keep it longer than the
 *     producer's {@code delivery.timeout.ms} so the relay always sees the final result
 * @param relay the publisher; switch it off e.g. in tests without Kafka
 */
@Validated
@ConfigurationProperties(prefix = "novabank.outbox")
public record OutboxProperties(
    @DefaultValue("false") boolean enabled,
    @DefaultValue("100") @Min(1) int batchSize,
    @DefaultValue("25s") Duration sendTimeout,
    @DefaultValue Relay relay) {

  /**
   * @param enabled runs the scheduled publisher
   * @param pollIntervalMs pause between relay runs
   */
  public record Relay(
      @DefaultValue("true") boolean enabled, @DefaultValue("1000") @Min(10) long pollIntervalMs) {}
}
