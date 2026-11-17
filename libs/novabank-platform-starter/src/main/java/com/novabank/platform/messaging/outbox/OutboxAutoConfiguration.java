package com.novabank.platform.messaging.outbox;

import com.novabank.platform.time.ClockAutoConfiguration;
import io.micrometer.observation.ObservationPredicate;
import io.micrometer.tracing.Tracer;
import io.micrometer.tracing.propagation.Propagator;
import java.time.Clock;
import org.springframework.beans.factory.ObjectProvider;
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
import org.springframework.scheduling.support.ScheduledTaskObservationContext;
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
  OutboxWriter outboxWriter(JdbcClient jdbcClient, JsonMapper jsonMapper, OutboxTracing tracing) {
    return new OutboxWriter(jdbcClient, jsonMapper, tracing);
  }

  /** Fallback when the service has no Micrometer Tracing (see {@link TracingConfiguration}). */
  @Bean
  @ConditionalOnMissingBean
  OutboxTracing outboxTracing() {
    return OutboxTracing.NONE;
  }

  @Configuration(proxyBeanMethods = false)
  @ConditionalOnClass(Tracer.class)
  static class TracingConfiguration {

    // Resolved when the bean is created, so it does not depend on auto-configuration order.
    @Bean
    @ConditionalOnMissingBean
    OutboxTracing outboxTracing(
        ObjectProvider<Tracer> tracer, ObjectProvider<Propagator> propagator) {
      Tracer resolvedTracer = tracer.getIfAvailable();
      Propagator resolvedPropagator = propagator.getIfAvailable();
      return resolvedTracer == null || resolvedPropagator == null
          ? OutboxTracing.NONE
          : new MicrometerOutboxTracing(resolvedTracer, resolvedPropagator);
    }
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
        Clock clock,
        OutboxTracing tracing,
        JsonMapper jsonMapper) {
      return new OutboxRelay(jdbcClient, kafkaTemplate, properties, clock, tracing, jsonMapper);
    }

    /**
     * The relay polls every second: tracing each poll would fill the trace store with empty traces.
     * Each published message still gets its own span, in the trace that wrote it.
     */
    @Bean
    ObservationPredicate outboxRelayPollNotObserved() {
      return (name, context) ->
          !(context instanceof ScheduledTaskObservationContext task
              && OutboxRelay.class.isAssignableFrom(task.getTargetClass()));
    }
  }
}
