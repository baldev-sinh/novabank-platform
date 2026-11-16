package com.novabank.ledger.infrastructure.messaging;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * @param ledgerEvents topic this service publishes to (owned here)
 * @param ledgerCommands topic this service takes commands from (owned here: the receiver defines
 *     its command contract)
 * @param accountEventsTopic consumed, owned by the account service
 * @param partitions partitions for every topic this service creates, incl. dead-letter topics
 */
@Validated
@ConfigurationProperties(prefix = "events")
public record EventsProperties(
    @NotBlank String ledgerEvents,
    @NotBlank String ledgerCommands,
    @NotBlank String accountEventsTopic,
    @Min(1) int partitions,
    @Min(1) short replicationFactor) {}
