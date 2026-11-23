package com.novabank.auth.infrastructure.configuration;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * {@code security.service-clients.*}: services that may get service tokens (7.2, Decision AF).
 *
 * @param tokenTtl lifetime of a service token; short, because service tokens can't be revoked
 * @param clients each calling service; empty means no service tokens are issued
 */
@Validated
@ConfigurationProperties(prefix = "security.service-clients")
public record ServiceClientsProperties(
    @NotNull @DefaultValue("5m") Duration tokenTtl,
    @NotNull @DefaultValue List<@Valid Client> clients) {

  /**
   * @param clientId the calling service, e.g. {@code payment-service}; becomes the token's subject
   * @param jwkSetUri where auth fetches the service's public key to check its signed client
   *     assertion (private_key_jwt); the service keeps the private key
   * @param scopes what the service may ask for, e.g. {@code accounts:read}
   */
  public record Client(
      @NotBlank String clientId, @NotBlank String jwkSetUri, @NotEmpty List<String> scopes) {}
}
