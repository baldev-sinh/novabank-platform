package com.novabank.customer.infrastructure.messaging;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

/**
 * Retries and dead-lettering come from the platform starter (1 s, 2 s, 4 s, then {@code
 * <topic>-dlt}); this service only creates the dead-letter topics for what it consumes.
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(EventsProperties.class)
public class KafkaConsumerConfiguration {

  /** The dead-letter topic is ours, so we create it; the source topic belongs to auth. */
  @Bean
  NewTopic userEventsDeadLetterTopic(EventsProperties properties) {
    return TopicBuilder.name(properties.deadLetterTopic())
        .partitions(properties.deadLetterPartitions())
        .replicas(properties.deadLetterReplicationFactor())
        .build();
  }
}
