package com.novabank.auth.infrastructure.configuration;


import org.springframework.boot.context.properties.ConfigurationProperties;


@ConfigurationProperties(prefix = "security.jwt")
public record JwtProperties(
    /**
     * Secret used to sign JWTs.
     */
     String secret,

    /**
     * Access token expiration in seconds.
     */
     long expiration,

    /**
     * Issuer of the token
     */
     String issuer
)
{ }
