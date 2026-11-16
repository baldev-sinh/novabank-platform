package com.novabank.auth.infrastructure.messaging.outbox;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(UserEventsProperties.class)
public class UserEventsTopicConfiguration {

  /** Created on startup if missing (Kafka auto-creation is off on purpose). */
  @Bean
  NewTopic userEventsTopic(UserEventsProperties properties) {
    return TopicBuilder.name(properties.topic())
        .partitions(properties.partitions())
        .replicas(properties.replicationFactor())
        .build();
  }
}
