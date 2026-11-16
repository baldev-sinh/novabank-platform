package com.novabank.platform.security;

import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;

/**
 * The security setup every NovaBank API shares. Each service still writes its own {@code
 * authorizeHttpRequests} rules.
 *
 * <pre>{@code
 * ResourceServerSecurity.apply(http, entryPoint, accessDeniedHandler)
 *     .authorizeHttpRequests(auth -> auth.anyRequest().authenticated());
 * }</pre>
 */
public final class ResourceServerSecurity {

  private ResourceServerSecurity() {}

  /** Stateless bearer-token API with RFC 9457 401/403 responses and {@code roles} → ROLE_*. */
  public static HttpSecurity apply(
      HttpSecurity http,
      AuthenticationEntryPoint entryPoint,
      AccessDeniedHandler accessDeniedHandler)
      throws Exception {
    return http
        // Bearer tokens, no cookies: there is no session to forge requests with, so no CSRF risk.
        .csrf(AbstractHttpConfigurer::disable)
        .sessionManagement(
            session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .exceptionHandling(exceptions ->
            exceptions
                .authenticationEntryPoint(entryPoint)
                .accessDeniedHandler(accessDeniedHandler))
        .oauth2ResourceServer(oauth2 ->
            oauth2
                .authenticationEntryPoint(entryPoint)
                .accessDeniedHandler(accessDeniedHandler)
                .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter())));
  }

  /** Maps the {@code roles} claim, e.g. {@code ["CUSTOMER"]}, to {@code ROLE_CUSTOMER}. */
  public static JwtAuthenticationConverter jwtAuthenticationConverter() {
    JwtGrantedAuthoritiesConverter authorities = new JwtGrantedAuthoritiesConverter();
    authorities.setAuthoritiesClaimName(JwtClaims.ROLES);
    authorities.setAuthorityPrefix("ROLE_");

    JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
    converter.setJwtGrantedAuthoritiesConverter(authorities);
    return converter;
  }
}
