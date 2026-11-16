package com.novabank.platform.security;

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
      NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(properties.jwkSetUri()).build();
      decoder.setJwtValidator(tokenValidator(properties.issuer(), properties.audience()));
      return decoder;
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
