package com.novabank.auth.infrastructure.messaging.outbox;

import java.time.Clock;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(OutboxProperties.class)
public class OutboxConfiguration {

  /** Created on startup if missing (Kafka auto-creation is off on purpose). */
  @Bean
  NewTopic userEventsTopic(OutboxProperties properties) {
    return TopicBuilder.name(properties.topic())
        .partitions(properties.partitions())
        .replicas(properties.replicationFactor())
        .build();
  }

  /** Can be switched off, e.g. in tests that don't run Kafka. */
  @Configuration(proxyBeanMethods = false)
  @EnableScheduling
  @ConditionalOnBooleanProperty(name = "outbox.relay.enabled", matchIfMissing = true)
  static class RelayConfiguration {

    @Bean
    OutboxRelay outboxRelay(
        SpringDataOutboxRepository repository,
        KafkaTemplate<String, String> kafkaTemplate,
        OutboxProperties properties,
        Clock clock) {
      return new OutboxRelay(repository, kafkaTemplate, properties, clock);
    }
  }
}
