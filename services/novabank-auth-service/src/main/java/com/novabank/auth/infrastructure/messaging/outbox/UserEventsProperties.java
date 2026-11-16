package com.novabank.auth.infrastructure.messaging.outbox;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * The topic this service publishes user events to. Auth owns it, so auth creates it.
 *
 * @param topic Kafka topic for user events
 * @param partitions more partitions allow more parallel consumers
 * @param replicationFactor copies of each partition (1 locally, 3 in production)
 */
@Validated
@ConfigurationProperties(prefix = "events.user-events")
public record UserEventsProperties(
    @NotBlank String topic, @Min(1) int partitions, @Min(1) short replicationFactor) {}
