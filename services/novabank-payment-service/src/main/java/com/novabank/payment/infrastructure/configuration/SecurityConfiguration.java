package com.novabank.payment.infrastructure.configuration;

import com.novabank.platform.security.ProblemAccessDeniedHandler;
import com.novabank.platform.security.ProblemAuthenticationEntryPoint;
import com.novabank.platform.security.ResourceServerSecurity;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

/** Customers make transfers; operations staff record deposits (Decision N). */
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
                    HttpMethod.GET,
                    "/livez",
                    "/readyz",
                    // /actuator/** is served only on the internal management port.
                    "/actuator/health/**",
                    "/actuator/prometheus",
                    "/swagger-ui/**",
                    "/v3/api-docs/**")
                .permitAll()
                .requestMatchers("/api/v1/admin/**")
                .hasAnyRole("OPERATIONS", "ADMIN")
                .requestMatchers("/api/v1/payments/**")
                .hasRole("CUSTOMER")
                .anyRequest()
                .denyAll());
    return http.build();
  }
}
