package com.novabank.audit.infrastructure.configuration;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;
import java.util.Set;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * {@code audit.*}.
 *
 * @param chainKey base64, at least 32 bytes, from the secret store (Decision AH). Changing it makes
 *     every existing entry fail verification, so it is rotated only with a new chain.
 * @param topics the business event topics to record
 * @param ignoredEventTypes recorded nowhere: periodic technical messages, not business facts
 * @param deadLetterPartitions at least the source topics' partitions (a failed record keeps its
 *     partition number)
 */
@Validated
@ConfigurationProperties(prefix = "audit")
public record AuditProperties(
    @NotBlank String chainKey,
    @NotEmpty List<String> topics,
    @DefaultValue Set<String> ignoredEventTypes,
    @Min(1) @DefaultValue("3") int deadLetterPartitions,
    @Min(1) @DefaultValue("3") short deadLetterReplicationFactor) {}
