package com.novabank.platform.messaging.consumer;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * Retries for a failed record before it goes to {@code <topic>-dlt}. Defaults: 1 s, 2 s, 4 s.
 *
 * @param initialInterval wait before the first retry
 * @param multiplier growth factor between retries
 * @param maxRetries retries after the first attempt
 */
@Validated
@ConfigurationProperties(prefix = "novabank.kafka.consumer.retry")
public record ConsumerRetryProperties(
    @DefaultValue("1s") Duration initialInterval,
    @DefaultValue("2.0") @DecimalMin("1.0") double multiplier,
    @DefaultValue("3") @Min(0) int maxRetries) {}
