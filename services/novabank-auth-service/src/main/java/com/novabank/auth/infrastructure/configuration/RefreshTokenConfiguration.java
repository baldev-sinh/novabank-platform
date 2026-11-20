package com.novabank.auth.infrastructure.configuration;

import com.novabank.auth.application.security.RefreshTokenSettings;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.validation.annotation.Validated;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(RefreshTokenConfiguration.RefreshTokenProperties.class)
public class RefreshTokenConfiguration {

  @Bean
  RefreshTokenSettings refreshTokenSettings(RefreshTokenProperties properties) {
    return new RefreshTokenSettings(
        properties.idleTimeout(), properties.absoluteLifetime(), properties.reuseGrace());
  }

  /**
   * {@code security.refresh-token.*} (7.1, Decision AE).
   *
   * @param cookieName the HttpOnly cookie that carries the refresh token
   * @param cookieSecure {@code Secure} attribute; browsers accept it on http://localhost too
   */
  @Validated
  @ConfigurationProperties(prefix = "security.refresh-token")
  public record RefreshTokenProperties(
      @NotNull @DefaultValue("30m") Duration idleTimeout,
      @NotNull @DefaultValue("8h") Duration absoluteLifetime,
      @NotNull @DefaultValue("10s") Duration reuseGrace,
      @NotBlank @DefaultValue("nb_refresh") String cookieName,
      @DefaultValue("true") boolean cookieSecure) {}
}
