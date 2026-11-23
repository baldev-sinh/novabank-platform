package com.novabank.platform.security;

import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.jwk.source.JWKSourceBuilder;
import com.nimbusds.jose.proc.SecurityContext;
import com.nimbusds.jose.util.DefaultResourceRetriever;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.util.List;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.jackson.autoconfigure.JacksonAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimNames;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtIssuerValidator;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import tools.jackson.databind.json.JsonMapper;

/** RFC 9457 401/403 handlers and, for services that validate tokens via JWKS, a JwtDecoder. */
@AutoConfiguration(after = JacksonAutoConfiguration.class)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnClass({JwtDecoder.class, JsonMapper.class})
public class PlatformSecurityAutoConfiguration {

  @Bean
  @ConditionalOnMissingBean
  ProblemAuthenticationEntryPoint problemAuthenticationEntryPoint(JsonMapper jsonMapper) {
    return new ProblemAuthenticationEntryPoint(jsonMapper);
  }

  @Bean
  @ConditionalOnMissingBean
  ProblemAccessDeniedHandler problemAccessDeniedHandler(JsonMapper jsonMapper) {
    return new ProblemAccessDeniedHandler(jsonMapper);
  }

  /**
   * Active when {@code security.jwt.jwk-set-uri} is set (even to an empty value, so a missing value
   * fails validation at startup). Backs off if the service defines its own JwtDecoder, as the auth
   * service does with its local key.
   */
  @Configuration(proxyBeanMethods = false)
  @ConditionalOnProperty(prefix = "security.jwt", name = "jwk-set-uri")
  @EnableConfigurationProperties(ResourceServerJwtProperties.class)
  static class JwksDecoderConfiguration {

    /**
     * Keys are fetched from the auth service's JWKS endpoint on first use and cached, so the
     * service starts even when the auth service is down, and picks up key rotation automatically.
     */
    @Bean
    @ConditionalOnMissingBean
    JwtDecoder jwtDecoder(ResourceServerJwtProperties properties) {
      // Nimbus fetches and caches the keys itself (RestTemplate is deprecated for removal in
      // Spring Framework 7.1, and Spring Security's JWK-set-URI builder only accepts one). Keys
      // are cached for 5 minutes and re-fetched for an unknown key id (rotation), at most every
      // 30 s; the timeouts bound the wait when that happens while auth is down (Decision U).
      JWKSource<SecurityContext> keys =
          JWKSourceBuilder.<SecurityContext>create(
                  jwkSetUrl(properties.jwkSetUri()),
                  new DefaultResourceRetriever(
                      Math.toIntExact(properties.connectTimeout().toMillis()),
                      Math.toIntExact(properties.readTimeout().toMillis()),
                      JWKSourceBuilder.DEFAULT_HTTP_SIZE_LIMIT))
              .build();
      NimbusJwtDecoder decoder =
          NimbusJwtDecoder.withJwkSource(keys).jwsAlgorithm(SignatureAlgorithm.RS256).build();
      decoder.setJwtValidator(tokenValidator(properties.issuer(), properties.audience()));
      return decoder;
    }
  }

  private static URL jwkSetUrl(String jwkSetUri) {
    try {
      return URI.create(jwkSetUri).toURL();
    } catch (IllegalArgumentException | MalformedURLException ex) {
      throw new IllegalStateException("security.jwt.jwk-set-uri is not a valid URL", ex);
    }
  }

  /** Signature and expiry are always checked; these also require our issuer and audience. */
  public static OAuth2TokenValidator<Jwt> tokenValidator(String issuer, String audience) {
    OAuth2TokenValidator<Jwt> audienceValidator =
        new JwtClaimValidator<List<String>>(
            JwtClaimNames.AUD, aud -> aud != null && aud.contains(audience));
    return JwtValidators.createDefaultWithValidators(
        new JwtIssuerValidator(issuer), audienceValidator);
  }
}
