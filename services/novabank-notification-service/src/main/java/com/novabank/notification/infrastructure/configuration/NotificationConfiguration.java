package com.novabank.notification.infrastructure.configuration;

import com.novabank.platform.messaging.consumer.DeadLetterTopics;
import java.util.stream.Stream;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaAdmin;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(NotificationProperties.class)
public class NotificationConfiguration {

  /** The source topics belong to auth, account and payment; the dead-letter topics are ours. */
  @Bean
  KafkaAdmin.NewTopics notificationDeadLetterTopics(
      NotificationProperties properties, @Value("${spring.kafka.consumer.group-id}") String group) {
    return new KafkaAdmin.NewTopics(
        Stream.of(
                properties.userEventsTopic(),
                properties.accountEventsTopic(),
                properties.paymentEventsTopic())
            .map(topic ->
                TopicBuilder.name(DeadLetterTopics.of(topic, group))
                    .partitions(properties.deadLetterPartitions())
                    .replicas(properties.deadLetterReplicationFactor())
                    .build())
            .toArray(NewTopic[]::new));
  }
}
