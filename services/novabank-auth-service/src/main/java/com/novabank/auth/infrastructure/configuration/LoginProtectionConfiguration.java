package com.novabank.auth.infrastructure.configuration;

import com.novabank.auth.application.security.LoginProtectionSettings;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.validation.annotation.Validated;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(LoginProtectionConfiguration.LoginProtectionProperties.class)
public class LoginProtectionConfiguration {

  @Bean
  LoginProtectionSettings loginProtectionSettings(LoginProtectionProperties properties) {
    return new LoginProtectionSettings(
        properties.maxFailedAttempts(), properties.failureWindow(), properties.lockoutDuration());
  }

  /** {@code security.login.*}: account lockout policy (Decision S). */
  @Validated
  @ConfigurationProperties(prefix = "security.login")
  public record LoginProtectionProperties(
      @Min(2) int maxFailedAttempts,
      @NotNull Duration failureWindow,
      @NotNull Duration lockoutDuration) {}
}
