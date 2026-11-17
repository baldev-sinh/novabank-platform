package com.novabank.platform.security;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * Where to find the auth service's public keys, and what a valid token must say. Used by every
 * service except auth itself, which signs the tokens.
 *
 * @param jwkSetUri the auth service's JWKS endpoint, e.g. {@code
 *     http://auth:8081/.well-known/jwks.json}
 * @param issuer expected {@code iss} claim
 * @param audience expected {@code aud} claim
 * @param connectTimeout for fetching the JWKS; the JDK default (30 s) would hold request threads
 *     far too long while auth is unreachable
 * @param readTimeout for fetching the JWKS
 */
@Validated
@ConfigurationProperties(prefix = "security.jwt")
public record ResourceServerJwtProperties(
    @NotBlank String jwkSetUri,
    @NotBlank String issuer,
    @NotBlank String audience,
    @NotNull @DefaultValue("2s") Duration connectTimeout,
    @NotNull @DefaultValue("3s") Duration readTimeout) {}
