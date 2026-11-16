package com.novabank.customer.support;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.kafka.config.TopicBuilder;
import org.testcontainers.kafka.KafkaContainer;

/**
 * A real Kafka broker (KRaft), same image as compose.yaml; sets spring.kafka.bootstrap-servers. The
 * source topic is owned by the auth service, so tests create it themselves.
 */
@TestConfiguration(proxyBeanMethods = false)
public class KafkaTestcontainersConfiguration {

  public static final String USER_EVENTS_TOPIC = "auth.user-events.v1";

  @Bean
  @ServiceConnection
  KafkaContainer kafka() {
    return new KafkaContainer("apache/kafka:4.4.0");
  }

  @Bean
  NewTopic userEventsTopic() {
    return TopicBuilder.name(USER_EVENTS_TOPIC).partitions(3).replicas(1).build();
  }
}
