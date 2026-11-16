package com.novabank.payment.infrastructure.messaging;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * @param paymentEvents published here (owned by this service)
 * @param accountCommands sent to; owned by the account service
 * @param ledgerCommands sent to; owned by the ledger service
 * @param accountEventsTopic replies, owned by the account service
 * @param ledgerEventsTopic replies, owned by the ledger service
 */
@Validated
@ConfigurationProperties(prefix = "events")
public record EventsProperties(
    @NotBlank String paymentEvents,
    @NotBlank String accountCommands,
    @NotBlank String ledgerCommands,
    @NotBlank String accountEventsTopic,
    @NotBlank String ledgerEventsTopic,
    @Min(1) int partitions,
    @Min(1) short replicationFactor) {}
