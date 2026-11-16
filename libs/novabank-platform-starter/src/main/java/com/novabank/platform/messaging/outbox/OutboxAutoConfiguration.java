package com.novabank.platform.messaging.outbox;

import com.novabank.platform.time.ClockAutoConfiguration;
import java.time.Clock;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.jackson.autoconfigure.JacksonAutoConfiguration;
import org.springframework.boot.jdbc.autoconfigure.JdbcClientAutoConfiguration;
import org.springframework.boot.kafka.autoconfigure.KafkaAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;
import tools.jackson.databind.json.JsonMapper;

/** Transactional outbox: opt in with {@code novabank.outbox.enabled=true}. */
@AutoConfiguration(
    after = {
      JdbcClientAutoConfiguration.class,
      KafkaAutoConfiguration.class,
      JacksonAutoConfiguration.class,
      ClockAutoConfiguration.class
    })
@ConditionalOnClass({JdbcClient.class, KafkaTemplate.class})
@ConditionalOnBooleanProperty("novabank.outbox.enabled")
@EnableConfigurationProperties(OutboxProperties.class)
public class OutboxAutoConfiguration {

  @Bean
  @ConditionalOnMissingBean
  OutboxWriter outboxWriter(JdbcClient jdbcClient, JsonMapper jsonMapper) {
    return new OutboxWriter(jdbcClient, jsonMapper);
  }

  @Configuration(proxyBeanMethods = false)
  @EnableScheduling
  @ConditionalOnBooleanProperty(name = "novabank.outbox.relay.enabled", matchIfMissing = true)
  static class RelayConfiguration {

    @Bean
    @ConditionalOnMissingBean
    OutboxRelay outboxRelay(
        JdbcClient jdbcClient,
        KafkaTemplate<String, String> kafkaTemplate,
        OutboxProperties properties,
        Clock clock) {
      return new OutboxRelay(jdbcClient, kafkaTemplate, properties, clock);
    }
  }
}
