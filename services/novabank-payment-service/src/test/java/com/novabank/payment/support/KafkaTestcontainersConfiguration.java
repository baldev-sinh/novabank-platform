package com.novabank.payment.support;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaAdmin;
import org.testcontainers.kafka.KafkaContainer;

/** Kafka, plus the topics the account and ledger services own (they aren't running here). */
@TestConfiguration(proxyBeanMethods = false)
public class KafkaTestcontainersConfiguration {

  @Bean
  @ServiceConnection
  KafkaContainer kafka() {
    return new KafkaContainer("apache/kafka:4.4.0");
  }

  @Bean
  KafkaAdmin.NewTopics otherServicesTopics() {
    return new KafkaAdmin.NewTopics(
        TopicBuilder.name("account.commands.v1").partitions(3).replicas(1).build(),
        TopicBuilder.name("account.events.v1").partitions(3).replicas(1).build(),
        TopicBuilder.name("ledger.commands.v1").partitions(3).replicas(1).build(),
        TopicBuilder.name("ledger.events.v1").partitions(3).replicas(1).build());
  }
}
