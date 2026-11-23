package com.novabank.payment.infrastructure.accounts;

import com.novabank.payment.application.port.AccountDirectory;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.micrometer.tagged.TaggedCircuitBreakerMetrics;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.net.http.HttpClient;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.web.client.OAuth2ClientHttpRequestInterceptor;
import org.springframework.security.oauth2.core.OAuth2AuthorizationException;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

/**
 * The account service client (7.3, Decision AG): service token, timeouts and a circuit breaker
 * whose state and calls are Micrometer metrics ({@code resilience4j_circuitbreaker_*}).
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(AccountDirectoryConfiguration.AccountServiceProperties.class)
public class AccountDirectoryConfiguration {

  /**
   * {@code novabank.account-service.*}.
   *
   * @param baseUrl e.g. {@code http://account-service:8083}
   * @param failureRateThreshold percentage of failed calls in the window that opens the circuit
   * @param slidingWindowSize the last N calls the failure rate is computed over
   * @param waitInOpenState how long the circuit stays open before letting trial calls through
   */
  @Validated
  @ConfigurationProperties(prefix = "novabank.account-service")
  public record AccountServiceProperties(
      @NotBlank String baseUrl,
      @NotNull @DefaultValue("1s") Duration connectTimeout,
      @NotNull @DefaultValue("2s") Duration readTimeout,
      @DefaultValue("50") float failureRateThreshold,
      @DefaultValue("20") int slidingWindowSize,
      @NotNull @DefaultValue("30s") Duration waitInOpenState) {}

  @Bean
  CircuitBreakerRegistry circuitBreakerRegistry(
      AccountServiceProperties properties, MeterRegistry meterRegistry) {
    CircuitBreakerConfig config =
        CircuitBreakerConfig.custom()
            .slidingWindow(
                properties.slidingWindowSize(),
                Math.max(1, properties.slidingWindowSize() / 2),
                CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
            .failureRateThreshold(properties.failureRateThreshold())
            .waitDurationInOpenState(properties.waitInOpenState())
            .permittedNumberOfCallsInHalfOpenState(3)
            // Network errors, timeouts, 5xx and no token are failures; a 4xx is an answer.
            .recordExceptions(
                ResourceAccessException.class,
                HttpServerErrorException.class,
                OAuth2AuthorizationException.class)
            .build();
    CircuitBreakerRegistry registry = CircuitBreakerRegistry.of(config);
    TaggedCircuitBreakerMetrics.ofCircuitBreakerRegistry(registry).bindTo(meterRegistry);
    return registry;
  }

  @Bean
  AccountDirectory accountDirectory(
      RestClient.Builder restClientBuilder,
      OAuth2AuthorizedClientManager serviceTokenManager,
      CircuitBreakerRegistry circuitBreakers,
      AccountServiceProperties properties) {
    JdkClientHttpRequestFactory requestFactory =
        new JdkClientHttpRequestFactory(
            HttpClient.newBuilder().connectTimeout(properties.connectTimeout()).build());
    requestFactory.setReadTimeout(properties.readTimeout());

    RestClient restClient =
        restClientBuilder
            .baseUrl(properties.baseUrl())
            .requestFactory(requestFactory)
            .requestInterceptor(new OAuth2ClientHttpRequestInterceptor(serviceTokenManager))
            .build();
    CircuitBreaker circuitBreaker = circuitBreakers.circuitBreaker("account-service");
    return new HttpAccountDirectory(restClient, circuitBreaker);
  }
}
