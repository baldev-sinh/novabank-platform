package com.novabank.account.infrastructure.messaging;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

/**
 * Topics this service owns: the one it publishes to and the dead-letter topics of what it consumes.
 * Retries and dead-lettering themselves come from the platform starter.
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(EventsProperties.class)
public class KafkaTopicsConfiguration {

  /** A dead-letter record keeps its partition number, so match the source's partitions. */
  static final int CUSTOMER_EVENTS_PARTITIONS = 3;

  @Bean
  NewTopic accountEventsTopic(EventsProperties properties) {
    EventsProperties.Topic topic = properties.accountEvents();
    return TopicBuilder.name(topic.topic())
        .partitions(topic.partitions())
        .replicas(topic.replicationFactor())
        .build();
  }

  @Bean
  NewTopic customerEventsDeadLetterTopic(EventsProperties properties) {
    return TopicBuilder.name(properties.customerEventsDeadLetterTopic())
        .partitions(CUSTOMER_EVENTS_PARTITIONS)
        .replicas(properties.deadLetterReplicationFactor())
        .build();
  }
}
