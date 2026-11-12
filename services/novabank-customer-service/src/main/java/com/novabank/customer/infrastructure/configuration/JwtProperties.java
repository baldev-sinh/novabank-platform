package com.novabank.customer.infrastructure.configuration;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Where to find the auth service's public keys, and what a valid token must say.
 *
 * @param jwkSetUri the auth service's JWKS endpoint, e.g. {@code
 *     http://auth:8081/.well-known/jwks.json}
 * @param issuer expected {@code iss} claim
 * @param audience expected {@code aud} claim
 */
@Validated
@ConfigurationProperties(prefix = "security.jwt")
public record JwtProperties(
    @NotBlank String jwkSetUri, @NotBlank String issuer, @NotBlank String audience) {}
