package com.novabank.audit.infrastructure.configuration;

import com.novabank.audit.domain.ChainHasher;
import com.novabank.platform.messaging.consumer.DeadLetterTopics;
import java.util.Base64;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaAdmin;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(AuditProperties.class)
public class AuditConfiguration {

  @Bean
  ChainHasher chainHasher(AuditProperties properties) {
    byte[] key;
    try {
      key = Base64.getDecoder().decode(properties.chainKey().trim());
    } catch (IllegalArgumentException ex) {
      // Never echo the value: it's a secret.
      throw new IllegalStateException("audit.chain-key is not valid base64", ex);
    }
    return new ChainHasher(key);
  }

  /** The source topics belong to other services; the dead-letter topics are ours. */
  @Bean
  KafkaAdmin.NewTopics auditDeadLetterTopics(
      AuditProperties properties, @Value("${spring.kafka.consumer.group-id}") String group) {
    return new KafkaAdmin.NewTopics(
        properties.topics().stream()
            .map(topic ->
                TopicBuilder.name(DeadLetterTopics.of(topic, group))
                    .partitions(properties.deadLetterPartitions())
                    .replicas(properties.deadLetterReplicationFactor())
                    .build())
            .toArray(NewTopic[]::new));
  }
}
