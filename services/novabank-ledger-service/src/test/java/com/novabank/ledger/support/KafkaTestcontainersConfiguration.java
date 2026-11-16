package com.novabank.ledger.support;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.kafka.config.TopicBuilder;
import org.testcontainers.kafka.KafkaContainer;

/** A real Kafka broker. The account events topic belongs to the account service. */
@TestConfiguration(proxyBeanMethods = false)
public class KafkaTestcontainersConfiguration {

  public static final String ACCOUNT_EVENTS_TOPIC = "account.events.v1";

  @Bean
  @ServiceConnection
  KafkaContainer kafka() {
    return new KafkaContainer("apache/kafka:4.4.0");
  }

  @Bean
  NewTopic accountEventsTopic() {
    return TopicBuilder.name(ACCOUNT_EVENTS_TOPIC).partitions(3).replicas(1).build();
  }
}
