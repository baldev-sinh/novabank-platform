package com.novabank.auth.infrastructure.configuration;

import com.novabank.platform.security.ProblemAccessDeniedHandler;
import com.novabank.platform.security.ProblemAuthenticationEntryPoint;
import com.novabank.platform.security.ResourceServerSecurity;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
@EnableConfigurationProperties({JwtProperties.class, CorsProperties.class})
@RequiredArgsConstructor
public class SecurityConfiguration {

  private final ProblemAuthenticationEntryPoint authenticationEntryPoint;
  private final ProblemAccessDeniedHandler accessDeniedHandler;

  @Bean
  public PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder();
  }

  @Bean
  public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
    ResourceServerSecurity.apply(http, authenticationEntryPoint, accessDeniedHandler)
        .cors(Customizer.withDefaults())
        .authorizeHttpRequests(auth ->
            auth.requestMatchers(
                    HttpMethod.POST,
                    "/api/v1/auth/login",
                    // Authenticated by the refresh token cookie, not by an access token.
                    "/api/v1/auth/refresh",
                    "/api/v1/auth/logout",
                    "/api/v1/users/register",
                    "/api/v1/users/verify-email",
                    "/api/v1/users/verify-email/resend")
                .permitAll()
                .requestMatchers(
                    HttpMethod.GET,
                    "/.well-known/jwks.json",
                    "/livez",
                    "/readyz",
                    // /actuator/** is served only on the internal management port.
                    "/actuator/health/**",
                    "/actuator/prometheus",
                    "/swagger-ui/**",
                    "/v3/api-docs/**")
                .permitAll()
                .requestMatchers("/api/v1/admin/**")
                .hasRole("ADMIN")
                .anyRequest()
                .authenticated());

    return http.build();
  }

  /**
   * Origins come from {@code security.cors.allowed-origins}. Credentials are allowed for the
   * refresh token cookie; the access token still travels in the Authorization header.
   */
  @Bean
  public CorsConfigurationSource corsConfigurationSource(CorsProperties cors) {
    CorsConfiguration configuration = new CorsConfiguration();
    configuration.setAllowedOrigins(List.copyOf(cors.allowedOrigins()));
    configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
    configuration.setAllowedHeaders(List.of("Authorization", "Content-Type", "X-Requested-With"));
    // The refresh token cookie (Decision AE) needs credentialed requests; still only the explicit
    // origins above, never a wildcard.
    configuration.setAllowCredentials(true);

    UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
    source.registerCorsConfiguration("/**", configuration);
    return source;
  }
}
