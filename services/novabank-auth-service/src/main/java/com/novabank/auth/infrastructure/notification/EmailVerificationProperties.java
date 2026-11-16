package com.novabank.auth.infrastructure.notification;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * @param linkTemplate frontend page that posts the token back, e.g. {@code
 *     https://app.example.com/verify-email#token={token}}. A URL fragment is never sent to a
 *     server, so the token doesn't end up in web server logs or Referer headers.
 */
@Validated
@ConfigurationProperties(prefix = "verification.email")
public record EmailVerificationProperties(
    @NotNull Duration tokenTtl,
    @NotNull Duration resendCooldown,
    @NotBlank @Email String from,
    @NotBlank @Pattern(regexp = ".*\\{token}.*", message = "must contain {token}") String linkTemplate) {}
