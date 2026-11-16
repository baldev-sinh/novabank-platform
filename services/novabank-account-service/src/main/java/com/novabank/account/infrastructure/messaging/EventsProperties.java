package com.novabank.account.infrastructure.messaging;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * @param accountEvents the topic this service owns and publishes to
 * @param customerEventsTopic consumed topic (owned by the customer service)
 * @param deadLetterReplicationFactor for this service's {@code -dlt} topics
 */
@Validated
@ConfigurationProperties(prefix = "events")
public record EventsProperties(
    Topic accountEvents,
    @NotBlank String customerEventsTopic,
    @Min(1) short deadLetterReplicationFactor) {

  public String customerEventsDeadLetterTopic() {
    return customerEventsTopic + "-dlt";
  }

  /**
   * @param partitions also used for dead-letter topics, which need at least the source's count
   */
  public record Topic(
      @NotBlank String topic, @Min(1) int partitions, @Min(1) short replicationFactor) {}
}
