package com.novabank.account.infrastructure.messaging;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * @param accountEvents published here (owned by this service)
 * @param accountCommands commands this service takes (owned here: the receiver defines them)
 * @param customerEventsTopic consumed, owned by the customer service
 * @param ledgerEventsTopic consumed, owned by the ledger service
 * @param deadLetterReplicationFactor for this service's {@code -dlt} topics
 */
@Validated
@ConfigurationProperties(prefix = "events")
public record EventsProperties(
    @NotNull Topic accountEvents,
    @NotNull Topic accountCommands,
    @NotBlank String customerEventsTopic,
    @NotBlank String ledgerEventsTopic,
    @Min(1) short deadLetterReplicationFactor) {

  /**
   * @param partitions also used for dead-letter topics, which need at least the source's count
   */
  public record Topic(
      @NotBlank String topic, @Min(1) int partitions, @Min(1) short replicationFactor) {}
}
