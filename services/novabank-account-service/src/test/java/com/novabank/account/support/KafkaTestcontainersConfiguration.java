package com.novabank.account.support;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.kafka.config.TopicBuilder;
import org.testcontainers.kafka.KafkaContainer;

/**
 * A real Kafka broker (KRaft), same image as compose.yaml. The customer events topic belongs to the
 * customer service, so tests create it themselves.
 */
@TestConfiguration(proxyBeanMethods = false)
public class KafkaTestcontainersConfiguration {

  public static final String CUSTOMER_EVENTS_TOPIC = "customer.events.v1";

  @Bean
  @ServiceConnection
  KafkaContainer kafka() {
    return new KafkaContainer("apache/kafka:4.4.0");
  }

  @Bean
  NewTopic customerEventsTopic() {
    return TopicBuilder.name(CUSTOMER_EVENTS_TOPIC).partitions(3).replicas(1).build();
  }
}
