package com.novabank.auth.infrastructure.messaging.outbox;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * @param topic Kafka topic for user events
 * @param partitions partitions of the topic; more partitions allow more parallel consumers
 * @param replicationFactor copies of each partition (1 locally, 3 in production)
 * @param batchSize max events published per relay run
 * @param sendTimeout how long to wait for Kafka to acknowledge one event
 */
@Validated
@ConfigurationProperties(prefix = "outbox")
public record OutboxProperties(
    @NotBlank String topic,
    @Min(1) int partitions,
    @Min(1) short replicationFactor,
    @Min(1) int batchSize,
    Duration sendTimeout) {

  public OutboxProperties {
    if (sendTimeout == null) {
      sendTimeout = Duration.ofSeconds(25);
    }
  }
}
