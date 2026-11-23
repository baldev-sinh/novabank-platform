package com.novabank.account.infrastructure.messaging;

import com.novabank.platform.messaging.consumer.DeadLetterTopics;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.beans.factory.annotation.Value;
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
  KafkaAdmin.NewTopics accountTopics(
      EventsProperties properties,
      @Value("${spring.kafka.consumer.group-id}") String consumerGroup) {
    short dlt = properties.deadLetterReplicationFactor();
    return new KafkaAdmin.NewTopics(
        topic(properties.accountEvents()),
        topic(properties.accountCommands()),
        topic(dlt(properties.accountCommands().topic(), consumerGroup), SOURCE_PARTITIONS, dlt),
        topic(dlt(properties.customerEventsTopic(), consumerGroup), SOURCE_PARTITIONS, dlt),
        topic(dlt(properties.ledgerEventsTopic(), consumerGroup), SOURCE_PARTITIONS, dlt));
  }

  private static String dlt(String topic, String consumerGroup) {
    return DeadLetterTopics.of(topic, consumerGroup);
  }

  private static NewTopic topic(EventsProperties.Topic topic) {
    return topic(topic.topic(), topic.partitions(), topic.replicationFactor());
  }

  private static NewTopic topic(String name, int partitions, short replicas) {
    return TopicBuilder.name(name).partitions(partitions).replicas(replicas).build();
  }
}
