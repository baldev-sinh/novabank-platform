package com.novabank.customer.infrastructure.messaging;

import com.novabank.platform.messaging.consumer.DeadLetterTopics;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "events")
public record EventsProperties(
    @NotBlank String userEventsTopic,
    @Min(1) int deadLetterPartitions,
    @Min(1) short deadLetterReplicationFactor) {

  public String deadLetterTopic(String consumerGroup) {
    return DeadLetterTopics.of(userEventsTopic, consumerGroup);
  }
}
