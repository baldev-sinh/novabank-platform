package com.novabank.customer.infrastructure.configuration;

import com.novabank.customer.infrastructure.security.JwtAccessDeniedHandler;
import com.novabank.customer.infrastructure.security.JwtAuthenticationEntryPoint;
import com.novabank.customer.infrastructure.security.JwtClaims;
import java.util.List;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimNames;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtIssuerValidator;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableConfigurationProperties(JwtProperties.class)
public class SecurityConfiguration {

  @Bean
  SecurityFilterChain securityFilterChain(
      HttpSecurity http,
      JwtAuthenticationEntryPoint entryPoint,
      JwtAccessDeniedHandler accessDeniedHandler)
      throws Exception {
    http
        // Stateless bearer-token API: no cookies, so no CSRF risk.
        .csrf(AbstractHttpConfigurer::disable)
        .sessionManagement(
            session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .exceptionHandling(exceptions ->
            exceptions
                .authenticationEntryPoint(entryPoint)
                .accessDeniedHandler(accessDeniedHandler))
        .authorizeHttpRequests(auth ->
            auth.requestMatchers(
                    HttpMethod.GET, "/actuator/health/**", "/swagger-ui/**", "/v3/api-docs/**")
                .permitAll()
                .requestMatchers("/api/v1/customers/**")
                .hasRole("CUSTOMER")
                .anyRequest()
                .authenticated())
        .oauth2ResourceServer(oauth2 ->
            oauth2
                .authenticationEntryPoint(entryPoint)
                .accessDeniedHandler(accessDeniedHandler)
                .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter())));
    return http.build();
  }

  /**
   * Keys are fetched from the auth service's JWKS endpoint on first use and cached, so this service
   * starts even when the auth service is down, and picks up key rotation automatically.
   */
  @Bean
  JwtDecoder jwtDecoder(JwtProperties properties) {
    NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(properties.jwkSetUri()).build();
    decoder.setJwtValidator(tokenValidator(properties));
    return decoder;
  }

  /** Signature and expiry are always checked; these also require our issuer and audience. */
  static OAuth2TokenValidator<Jwt> tokenValidator(JwtProperties properties) {
    OAuth2TokenValidator<Jwt> audience =
        new JwtClaimValidator<List<String>>(
            JwtClaimNames.AUD, aud -> aud != null && aud.contains(properties.audience()));
    return JwtValidators.createDefaultWithValidators(
        new JwtIssuerValidator(properties.issuer()), audience);
  }

  /** Maps the {@code roles} claim, e.g. {@code ["CUSTOMER"]}, to {@code ROLE_CUSTOMER}. */
  private static JwtAuthenticationConverter jwtAuthenticationConverter() {
    JwtGrantedAuthoritiesConverter authorities = new JwtGrantedAuthoritiesConverter();
    authorities.setAuthoritiesClaimName(JwtClaims.ROLES);
    authorities.setAuthorityPrefix("ROLE_");

    JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
    converter.setJwtGrantedAuthoritiesConverter(authorities);
    return converter;
  }
}
