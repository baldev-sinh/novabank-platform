package com.novabank.platform.messaging.consumer;

import com.novabank.platform.messaging.InvalidMessageException;
import com.novabank.platform.time.ClockAutoConfiguration;
import java.time.Clock;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.jdbc.autoconfigure.JdbcClientAutoConfiguration;
import org.springframework.boot.kafka.autoconfigure.KafkaAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.CommonErrorHandler;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.ExponentialBackOff;

/**
 * Error handling for {@code @KafkaListener}s: retry with exponential backoff, then publish to
 * {@code <topic>-dlt} on the same partition, so one bad record never blocks its partition. {@link
 * InvalidMessageException} skips the retries. Boot applies a {@link CommonErrorHandler} bean to its
 * auto-configured listener container factory
 * (https://docs.spring.io/spring-kafka/reference/kafka/annotation-error-handling.html).
 *
 * <p>Each consumer creates its own dead-letter topics: it knows which topics it reads.
 */
@AutoConfiguration(
    after = {
      KafkaAutoConfiguration.class,
      JdbcClientAutoConfiguration.class,
      ClockAutoConfiguration.class
    })
@ConditionalOnClass(DefaultErrorHandler.class)
@EnableConfigurationProperties(ConsumerRetryProperties.class)
public class KafkaConsumerAutoConfiguration {

  @Bean
  @ConditionalOnMissingBean
  @ConditionalOnBean(KafkaTemplate.class)
  CommonErrorHandler kafkaErrorHandler(
      KafkaTemplate<Object, Object> kafkaTemplate, ConsumerRetryProperties retry) {
    ExponentialBackOff backOff =
        new ExponentialBackOff(retry.initialInterval().toMillis(), retry.multiplier());
    // Retries after the first attempt (Spring Framework 7 ExponentialBackOff#setMaxAttempts).
    backOff.setMaxAttempts(retry.maxRetries());

    DefaultErrorHandler handler =
        new DefaultErrorHandler(new DeadLetterPublishingRecoverer(kafkaTemplate), backOff);
    handler.addNotRetryableExceptions(InvalidMessageException.class);
    return handler;
  }

  @Configuration(proxyBeanMethods = false)
  @ConditionalOnClass(JdbcClient.class)
  static class IdempotencyConfiguration {

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBean(JdbcClient.class)
    ProcessedEvents processedEvents(JdbcClient jdbcClient, Clock clock) {
      return new ProcessedEvents(jdbcClient, clock);
    }
  }
}
