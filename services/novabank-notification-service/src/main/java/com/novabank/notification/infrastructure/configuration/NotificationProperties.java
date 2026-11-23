package com.novabank.notification.infrastructure.configuration;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * {@code notifications.*}.
 *
 * @param maxEventAge older events update the projection but send no email: a new consumer group
 *     starts from the oldest retained event, and nobody wants last month's transfers emailed today
 * @param batchSize notifications one dispatcher run claims
 * @param lease how long a claimed notification stays hidden from other instances; longer than the
 *     mail timeouts, so a slow send isn't picked up twice
 * @param retryInitial first retry delay, doubling up to {@code retryMax}
 * @param maxAttempts then the notification is FAILED (metric {@code novabank_notifications_failed})
 */
@Validated
@ConfigurationProperties(prefix = "notifications")
public record NotificationProperties(
    @NotBlank @Email String from,
    @NotNull @DefaultValue("24h") Duration maxEventAge,
    @Min(1) @DefaultValue("20") int batchSize,
    @NotNull @DefaultValue("2m") Duration lease,
    @NotNull @DefaultValue("30s") Duration retryInitial,
    @NotNull @DefaultValue("1h") Duration retryMax,
    @Min(1) @DefaultValue("10") int maxAttempts,
    @NotBlank String userEventsTopic,
    @NotBlank String accountEventsTopic,
    @NotBlank String paymentEventsTopic,
    @Min(1) @DefaultValue("3") int deadLetterPartitions,
    @Min(1) @DefaultValue("3") short deadLetterReplicationFactor) {

  /** {@code retryInitial × 2^(attempt-1)}, capped at {@code retryMax}. */
  public Duration retryDelay(int attempt) {
    int doublings = Math.min(Math.max(attempt - 1, 0), 20);
    Duration delay = retryInitial.multipliedBy(1L << doublings);
    return delay.compareTo(retryMax) > 0 ? retryMax : delay;
  }
}
