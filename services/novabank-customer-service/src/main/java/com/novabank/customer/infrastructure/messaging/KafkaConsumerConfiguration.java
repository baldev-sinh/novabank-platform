package com.novabank.customer.infrastructure.messaging;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.CommonErrorHandler;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.ExponentialBackOff;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(EventsProperties.class)
public class KafkaConsumerConfiguration {

  /**
   * Failed records are retried with backoff (1 s, 2 s, 4 s), then sent to {@code <topic>-dlt} so
   * one bad record never blocks its partition. Invalid events skip the retries. Spring Boot applies
   * this handler to the auto-configured listener container factory.
   */
  @Bean
  CommonErrorHandler kafkaErrorHandler(KafkaTemplate<Object, Object> kafkaTemplate) {
    ExponentialBackOff backOff = new ExponentialBackOff(1000, 2.0);
    backOff.setMaxAttempts(3);

    DefaultErrorHandler handler =
        new DefaultErrorHandler(new DeadLetterPublishingRecoverer(kafkaTemplate), backOff);
    handler.addNotRetryableExceptions(InvalidEventException.class);
    return handler;
  }

  /** The dead-letter topic is ours, so we create it; the source topic belongs to auth. */
  @Bean
  NewTopic userEventsDeadLetterTopic(EventsProperties properties) {
    return TopicBuilder.name(properties.deadLetterTopic())
        .partitions(properties.deadLetterPartitions())
        .replicas(properties.deadLetterReplicationFactor())
        .build();
  }
}
