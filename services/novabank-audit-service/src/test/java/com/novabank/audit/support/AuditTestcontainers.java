package com.novabank.audit.support;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaAdmin;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * PostgreSQL and Kafka, same images as compose.yaml. The source topics belong to the other
 * services, so the tests create them.
 */
@TestConfiguration(proxyBeanMethods = false)
public class AuditTestcontainers {

  @Bean
  @ServiceConnection
  PostgreSQLContainer postgres() {
    return new PostgreSQLContainer("postgres:18.6-trixie");
  }

  @Bean
  @ServiceConnection
  KafkaContainer kafka() {
    return new KafkaContainer("apache/kafka:4.4.0");
  }

  @Bean
  KafkaAdmin.NewTopics sourceTopics() {
    return new KafkaAdmin.NewTopics(
        TopicBuilder.name("auth.user-events.v1").partitions(3).replicas(1).build(),
        TopicBuilder.name("customer.events.v1").partitions(3).replicas(1).build(),
        TopicBuilder.name("account.events.v1").partitions(3).replicas(1).build(),
        TopicBuilder.name("ledger.events.v1").partitions(3).replicas(1).build(),
        TopicBuilder.name("payment.events.v1").partitions(3).replicas(1).build());
  }
}
