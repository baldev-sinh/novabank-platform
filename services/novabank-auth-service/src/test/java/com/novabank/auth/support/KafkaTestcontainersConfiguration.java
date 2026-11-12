package com.novabank.auth.support;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.kafka.KafkaContainer;

/** A real Kafka broker (KRaft), same image as compose.yaml; sets spring.kafka.bootstrap-servers. */
@TestConfiguration(proxyBeanMethods = false)
public class KafkaTestcontainersConfiguration {

  @Bean
  @ServiceConnection
  KafkaContainer kafka() {
    return new KafkaContainer("apache/kafka:4.4.0");
  }
}
