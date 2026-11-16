package com.novabank.payment.infrastructure.messaging;

import org.apache.kafka.clients.admin.NewTopic;
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
  KafkaAdmin.NewTopics paymentTopics(EventsProperties properties) {
    return new KafkaAdmin.NewTopics(
        topic(properties.paymentEvents(), properties),
        topic(properties.accountEventsTopic() + "-dlt", properties),
        topic(properties.ledgerEventsTopic() + "-dlt", properties));
  }

  private static NewTopic topic(String name, EventsProperties properties) {
    return TopicBuilder.name(name)
        .partitions(properties.partitions())
        .replicas(properties.replicationFactor())
        .build();
  }
}
