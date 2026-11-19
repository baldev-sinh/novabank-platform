package com.novabank.auth.infrastructure.configuration;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Browser origins allowed to call auth cross-origin (the Angular app). Empty means none: a
 * deployment must name its frontend explicitly.
 *
 * @param allowedOrigins exact origins, e.g. {@code https://app.novabank.example}
 */
@ConfigurationProperties(prefix = "security.cors")
public record CorsProperties(@DefaultValue List<String> allowedOrigins) {}
