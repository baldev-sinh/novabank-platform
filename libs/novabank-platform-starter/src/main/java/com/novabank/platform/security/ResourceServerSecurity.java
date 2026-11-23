package com.novabank.platform.security;

import java.util.ArrayList;
import java.util.List;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.GrantedAuthority;
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

  /**
   * Maps the {@code roles} claim of user tokens, e.g. {@code ["CUSTOMER"]}, to {@code
   * ROLE_CUSTOMER}, and the {@code scope} claim of service tokens (7.2), e.g. {@code
   * accounts:read}, to {@code SCOPE_accounts:read}. A token has one or the other.
   */
  public static JwtAuthenticationConverter jwtAuthenticationConverter() {
    JwtGrantedAuthoritiesConverter roles = new JwtGrantedAuthoritiesConverter();
    roles.setAuthoritiesClaimName(JwtClaims.ROLES);
    roles.setAuthorityPrefix("ROLE_");
    // Default: the "scope"/"scp" claim with the SCOPE_ prefix.
    JwtGrantedAuthoritiesConverter scopes = new JwtGrantedAuthoritiesConverter();

    JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
    converter.setJwtGrantedAuthoritiesConverter(jwt -> {
      List<GrantedAuthority> authorities = new ArrayList<>(roles.convert(jwt));
      authorities.addAll(scopes.convert(jwt));
      return authorities;
    });
    return converter;
  }
}
