package com.novabank.platform;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.novabank.platform.idempotency.IdempotencyAutoConfiguration;
import com.novabank.platform.idempotency.IdempotentRequests;
import com.novabank.platform.messaging.consumer.KafkaConsumerAutoConfiguration;
import com.novabank.platform.messaging.consumer.ProcessedEvents;
import com.novabank.platform.messaging.outbox.MicrometerOutboxTracing;
import com.novabank.platform.messaging.outbox.OutboxAutoConfiguration;
import com.novabank.platform.messaging.outbox.OutboxRelay;
import com.novabank.platform.messaging.outbox.OutboxTracing;
import com.novabank.platform.messaging.outbox.OutboxWriter;
import com.novabank.platform.security.PlatformSecurityAutoConfiguration;
import com.novabank.platform.security.ProblemAccessDeniedHandler;
import com.novabank.platform.security.ProblemAuthenticationEntryPoint;
import com.novabank.platform.time.ClockAutoConfiguration;
import com.novabank.platform.web.ProblemTraceIdAdvice;
import com.novabank.platform.web.WebProblemAutoConfiguration;
import io.micrometer.observation.ObservationPredicate;
import io.micrometer.tracing.Tracer;
import io.micrometer.tracing.propagation.Propagator;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.jackson.autoconfigure.JacksonAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.CommonErrorHandler;
import org.springframework.scheduling.support.ScheduledTaskObservationContext;
import org.springframework.security.oauth2.jwt.JwtDecoder;

/** The starter's conditions: what is switched on, when, and when it backs off. */
class PlatformAutoConfigurationTest {

  private final WebApplicationContextRunner web =
      new WebApplicationContextRunner()
          .withConfiguration(
              AutoConfigurations.of(
                  JacksonAutoConfiguration.class, PlatformSecurityAutoConfiguration.class));

  private final ApplicationContextRunner messaging =
      new ApplicationContextRunner()
          .withConfiguration(
              AutoConfigurations.of(
                  JacksonAutoConfiguration.class,
                  ClockAutoConfiguration.class,
                  OutboxAutoConfiguration.class,
                  KafkaConsumerAutoConfiguration.class,
                  IdempotencyAutoConfiguration.class))
          .withBean(JdbcClient.class, () -> mock(JdbcClient.class))
          .withBean(KafkaTemplate.class, () -> mock(KafkaTemplate.class));

  @Test
  @DisplayName("401/403 handlers are always provided; no JWKS decoder without security.jwt.*")
  void securityHandlersWithoutDecoder() {
    web.run(context -> {
      assertThat(context).hasSingleBean(ProblemAuthenticationEntryPoint.class);
      assertThat(context).hasSingleBean(ProblemAccessDeniedHandler.class);
      assertThat(context).doesNotHaveBean(JwtDecoder.class);
    });
  }

  @Test
  @DisplayName("A JWKS decoder is created from security.jwt.*")
  void jwksDecoder() {
    web.withPropertyValues(
            "security.jwt.jwk-set-uri=http://auth.test/.well-known/jwks.json",
            "security.jwt.issuer=http://auth.test",
            "security.jwt.audience=novabank-api")
        .run(context -> assertThat(context).hasSingleBean(JwtDecoder.class));
  }

  @Test
  @DisplayName("A missing issuer fails startup instead of accepting any issuer")
  void jwksDecoderRequiresIssuer() {
    web.withPropertyValues(
            "security.jwt.jwk-set-uri=http://auth.test/.well-known/jwks.json",
            "security.jwt.issuer=",
            "security.jwt.audience=novabank-api")
        .run(context -> assertThat(context).hasFailed());
  }

  @Test
  @DisplayName("The service's own JwtDecoder wins (the auth service signs its own tokens)")
  void ownDecoderWins() {
    JwtDecoder own = mock(JwtDecoder.class);
    web.withBean(JwtDecoder.class, () -> own)
        .withPropertyValues(
            "security.jwt.jwk-set-uri=http://auth.test/.well-known/jwks.json",
            "security.jwt.issuer=http://auth.test",
            "security.jwt.audience=novabank-api")
        .run(context -> assertThat(context.getBean(JwtDecoder.class)).isSameAs(own));
  }

  @Test
  @DisplayName("The outbox is opt-in; idempotency registry and DLT error handler are always there")
  void outboxIsOptIn() {
    messaging.run(context -> {
      assertThat(context).doesNotHaveBean(OutboxWriter.class);
      assertThat(context).doesNotHaveBean(OutboxRelay.class);
      assertThat(context).hasSingleBean(ProcessedEvents.class);
      assertThat(context).hasSingleBean(IdempotentRequests.class);
      assertThat(context).hasSingleBean(CommonErrorHandler.class);
      assertThat(context).hasSingleBean(Clock.class);
    });
  }

  @Test
  @DisplayName("Enabling the outbox adds the writer and relay; the relay can be switched off")
  void outboxEnabled() {
    messaging
        .withPropertyValues(
            "novabank.outbox.enabled=true", "novabank.outbox.relay.poll-interval-ms=60000")
        .run(context -> {
          assertThat(context).hasSingleBean(OutboxWriter.class);
          assertThat(context).hasSingleBean(OutboxRelay.class);
        });
    messaging
        .withPropertyValues("novabank.outbox.enabled=true", "novabank.outbox.relay.enabled=false")
        .run(context -> {
          assertThat(context).hasSingleBean(OutboxWriter.class);
          assertThat(context).doesNotHaveBean(OutboxRelay.class);
        });
  }

  @Test
  @DisplayName("A service's own Clock wins")
  void ownClockWins() {
    Clock fixed = Clock.fixed(Instant.EPOCH, ZoneOffset.UTC);
    messaging
        .withBean(Clock.class, () -> fixed)
        .run(context -> assertThat(context.getBean(Clock.class)).isSameAs(fixed));
  }

  @Test
  @DisplayName("Without a tracer the outbox stores no trace context")
  void outboxWithoutTracer() {
    messaging
        .withPropertyValues("novabank.outbox.enabled=true", "novabank.outbox.relay.enabled=false")
        .run(context ->
            assertThat(context.getBean(OutboxTracing.class)).isSameAs(OutboxTracing.NONE));
  }

  @Test
  @DisplayName("With a tracer and propagator the outbox carries the trace")
  void outboxWithTracer() {
    messaging
        .withBean(Tracer.class, () -> mock(Tracer.class))
        .withBean(Propagator.class, () -> mock(Propagator.class))
        .withPropertyValues("novabank.outbox.enabled=true", "novabank.outbox.relay.enabled=false")
        .run(context ->
            assertThat(context)
                .getBean(OutboxTracing.class)
                .isInstanceOf(MicrometerOutboxTracing.class));
  }

  @Test
  @DisplayName("The relay's poll is not observed; other scheduled tasks still are")
  void relayPollNotObserved() {
    messaging
        .withPropertyValues(
            "novabank.outbox.enabled=true", "novabank.outbox.relay.poll-interval-ms=60000")
        .run(context -> {
          ObservationPredicate predicate = context.getBean(ObservationPredicate.class);
          ScheduledTaskObservationContext relayPoll =
              new ScheduledTaskObservationContext(
                  context.getBean(OutboxRelay.class),
                  OutboxRelay.class.getMethod("publishPending"));
          ScheduledTaskObservationContext otherTask =
              new ScheduledTaskObservationContext(
                  this,
                  PlatformAutoConfigurationTest.class.getDeclaredMethod("relayPollNotObserved"));

          assertThat(predicate.test("tasks.scheduled.execution", relayPoll)).isFalse();
          assertThat(predicate.test("tasks.scheduled.execution", otherTask)).isTrue();
        });
  }

  @Test
  @DisplayName("Web services get the trace id on every Problem Details body")
  void problemTraceIdAdvice() {
    new WebApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(WebProblemAutoConfiguration.class))
        .run(context -> assertThat(context).hasSingleBean(ProblemTraceIdAdvice.class));
  }
}
