package com.novabank.customer.infrastructure.configuration;

import com.novabank.platform.security.ProblemAccessDeniedHandler;
import com.novabank.platform.security.ProblemAuthenticationEntryPoint;
import com.novabank.platform.security.ResourceServerSecurity;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Tokens are validated against the auth service's JWKS (issuer + audience checked); the decoder
 * comes from the platform starter ({@code security.jwt.*}).
 */
@Configuration
public class SecurityConfiguration {

  @Bean
  SecurityFilterChain securityFilterChain(
      HttpSecurity http,
      ProblemAuthenticationEntryPoint entryPoint,
      ProblemAccessDeniedHandler accessDeniedHandler)
      throws Exception {
    ResourceServerSecurity.apply(http, entryPoint, accessDeniedHandler)
        .authorizeHttpRequests(auth ->
            auth.requestMatchers(
                    HttpMethod.GET, "/actuator/health/**", "/swagger-ui/**", "/v3/api-docs/**")
                .permitAll()
                .requestMatchers("/api/v1/compliance/**")
                .hasRole("COMPLIANCE_OFFICER")
                .requestMatchers("/api/v1/customers/**")
                .hasRole("CUSTOMER")
                .anyRequest()
                .authenticated());
    return http.build();
  }
}
