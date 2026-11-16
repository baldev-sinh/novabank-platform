package com.novabank.customer.infrastructure.messaging;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * The topic this service publishes customer events to. Customer owns it, so customer creates it.
 *
 * @param topic Kafka topic for customer events
 * @param partitions more partitions allow more parallel consumers
 * @param replicationFactor copies of each partition (1 locally, 3 in production)
 */
@Validated
@ConfigurationProperties(prefix = "events.customer-events")
public record CustomerEventsProperties(
    @NotBlank String topic, @Min(1) int partitions, @Min(1) short replicationFactor) {}
