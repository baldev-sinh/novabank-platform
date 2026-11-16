package com.novabank.payment.infrastructure.configuration;

import com.novabank.payment.application.settings.SagaSettings;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.validation.annotation.Validated;

/**
 * @param stepTimeout wait for a reply before re-sending a step
 * @param maxAttempts re-sends per step before the payment is flagged for operations staff
 * @param maxAmount largest single payment (any currency), a simple limit until real limits exist
 */
@Validated
@ConfigurationProperties(prefix = "payments")
public record PaymentProperties(
    @NotNull Duration stepTimeout, @Min(1) int maxAttempts, @NotNull BigDecimal maxAmount) {

  @Configuration(proxyBeanMethods = false)
  @EnableScheduling
  @EnableConfigurationProperties(PaymentProperties.class)
  static class SettingsConfiguration {

    @Bean
    SagaSettings sagaSettings(PaymentProperties properties) {
      return new SagaSettings(properties.stepTimeout(), properties.maxAttempts());
    }
  }
}
