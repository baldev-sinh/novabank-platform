package com.novabank.auth.infrastructure.configuration;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Access token settings. Tokens are signed with RS256; other services verify them through the
 * public JWKS endpoint, so only this service ever holds the private key.
 *
 * @param issuer value of the {@code iss} claim, and what resource servers expect
 * @param audience value of the {@code aud} claim, and what resource servers expect
 * @param accessTokenTtl how long an access token stays valid
 * @param privateKeyLocation PKCS#8 PEM private key, e.g. {@code file:/run/secrets/jwt-private.pem}
 * @param publicKeyLocation X.509 PEM public key matching the private key
 * @param generateEphemeralKey generate a throwaway key pair at startup instead (local dev only;
 *     tokens stop working after a restart and are not shared between instances)
 */
@Validated
@ConfigurationProperties(prefix = "security.jwt")
public record JwtProperties(
    @NotBlank String issuer,
    @NotBlank String audience,
    @NotNull Duration accessTokenTtl,
    String privateKeyLocation,
    String publicKeyLocation,
    boolean generateEphemeralKey) {}
