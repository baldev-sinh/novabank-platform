package com.novabank.platform.messaging.consumer;

import com.novabank.platform.messaging.InvalidMessageException;
import com.novabank.platform.time.ClockAutoConfiguration;
import java.time.Clock;
import java.util.function.BiFunction;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.TopicPartition;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.jdbc.autoconfigure.JdbcClientAutoConfiguration;
import org.springframework.boot.kafka.autoconfigure.KafkaAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.CommonErrorHandler;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.listener.ListenerExecutionFailedException;
import org.springframework.util.StringUtils;
import org.springframework.util.backoff.ExponentialBackOff;

/**
 * Error handling for {@code @KafkaListener}s: retry with exponential backoff, then publish to
 * {@code <topic>.<group>-dlt} ({@link DeadLetterTopics}) on the same partition, so one bad record
 * never blocks its partition. {@link InvalidMessageException} skips the retries. Boot applies a
 * {@link CommonErrorHandler} bean to its auto-configured listener container factory
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
      KafkaTemplate<Object, Object> kafkaTemplate,
      ConsumerRetryProperties retry,
      Environment environment) {
    ExponentialBackOff backOff =
        new ExponentialBackOff(retry.initialInterval().toMillis(), retry.multiplier());
    // Retries after the first attempt (Spring Framework 7 ExponentialBackOff#setMaxAttempts).
    backOff.setMaxAttempts(retry.maxRetries());

    DefaultErrorHandler handler =
        new DefaultErrorHandler(
            new DeadLetterPublishingRecoverer(
                kafkaTemplate,
                deadLetterDestination(environment.getProperty("spring.kafka.consumer.group-id"))),
            backOff);
    handler.addNotRetryableExceptions(InvalidMessageException.class);
    return handler;
  }

  /**
   * The failing listener's group (a listener may override the service's default group), else the
   * service's {@code spring.kafka.consumer.group-id}. Same partition as the source record.
   */
  static BiFunction<ConsumerRecord<?, ?>, Exception, TopicPartition> deadLetterDestination(
      String defaultGroup) {
    return (record, exception) -> {
      String group =
          exception instanceof ListenerExecutionFailedException failed
                  && StringUtils.hasText(failed.getGroupId())
              ? failed.getGroupId()
              : defaultGroup;
      return new TopicPartition(DeadLetterTopics.of(record.topic(), group), record.partition());
    };
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
