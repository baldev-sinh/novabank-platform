package com.novabank.auth.infrastructure.security.ratelimit;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Per-client-IP limits on the public, unauthenticated endpoints (Decision T).
 *
 * @param enabled switch off only in tests that are not about rate limiting
 * @param rules one token bucket per (rule, IP): {@code capacity} requests at once, refilled
 *     smoothly to {@code capacity} per {@code period}
 */
@Validated
@ConfigurationProperties(prefix = "security.rate-limit")
public record RateLimitProperties(boolean enabled, @NotNull List<@Valid Rule> rules) {

  /**
   * @param name short, stable id used in the bucket key, e.g. {@code login}
   * @param path exact request path, matched for POST requests
   */
  public record Rule(
      @NotBlank String name,
      @NotBlank String path,
      @Positive long capacity,
      @NotNull Duration period) {}
}
