package com.novabank.payment.infrastructure.messaging;

import com.novabank.platform.messaging.consumer.DeadLetterTopics;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaAdmin;

/** The topic this service owns, and the dead-letter topics of what it consumes. */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(EventsProperties.class)
public class KafkaTopicsConfiguration {

  @Bean
  KafkaAdmin.NewTopics paymentTopics(
      EventsProperties properties,
      @Value("${spring.kafka.consumer.group-id}") String consumerGroup) {
    return new KafkaAdmin.NewTopics(
        topic(properties.paymentEvents(), properties),
        topic(DeadLetterTopics.of(properties.accountEventsTopic(), consumerGroup), properties),
        topic(DeadLetterTopics.of(properties.ledgerEventsTopic(), consumerGroup), properties));
  }

  private static NewTopic topic(String name, EventsProperties properties) {
    return TopicBuilder.name(name)
        .partitions(properties.partitions())
        .replicas(properties.replicationFactor())
        .build();
  }
}
