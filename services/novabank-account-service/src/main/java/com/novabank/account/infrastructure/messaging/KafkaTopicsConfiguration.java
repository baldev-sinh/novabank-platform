package com.novabank.account.infrastructure.messaging;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaAdmin;

/**
 * Topics this service owns (events it publishes, commands it takes) and the dead-letter topics of
 * everything it consumes. Retries and dead-lettering come from the platform starter.
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(EventsProperties.class)
public class KafkaTopicsConfiguration {

  /** A dead-letter record keeps its partition number, so match the sources' partitions. */
  static final int SOURCE_PARTITIONS = 3;

  @Bean
  KafkaAdmin.NewTopics accountTopics(EventsProperties properties) {
    short dlt = properties.deadLetterReplicationFactor();
    return new KafkaAdmin.NewTopics(
        topic(properties.accountEvents()),
        topic(properties.accountCommands()),
        topic(properties.accountCommands().topic() + "-dlt", SOURCE_PARTITIONS, dlt),
        topic(properties.customerEventsTopic() + "-dlt", SOURCE_PARTITIONS, dlt),
        topic(properties.ledgerEventsTopic() + "-dlt", SOURCE_PARTITIONS, dlt));
  }

  private static NewTopic topic(EventsProperties.Topic topic) {
    return topic(topic.topic(), topic.partitions(), topic.replicationFactor());
  }

  private static NewTopic topic(String name, int partitions, short replicas) {
    return TopicBuilder.name(name).partitions(partitions).replicas(replicas).build();
  }
}
