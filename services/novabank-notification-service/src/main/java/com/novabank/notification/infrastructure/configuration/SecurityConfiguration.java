package com.novabank.notification.infrastructure.configuration;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderNotFoundException;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

/**
 * No API: this service only consumes events and sends emails. Everything but the probes is refused,
 * so a future endpoint is closed until someone opens it on purpose.
 */
@Configuration
public class SecurityConfiguration {

  @Bean
  SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
    return http.csrf(csrf -> csrf.disable())
        .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .httpBasic(basic -> basic.disable())
        .formLogin(form -> form.disable())
        .authorizeHttpRequests(auth ->
            auth.requestMatchers(
                    HttpMethod.GET,
                    "/livez",
                    "/readyz",
                    // /actuator/** is served only on the internal management port.
                    "/actuator/health/**",
                    "/actuator/prometheus")
                .permitAll()
                .anyRequest()
                .denyAll())
        .build();
  }

  /**
   * Nobody signs in here. Without an authentication bean of its own, Boot would add a default user
   * with a random password and log that password at every start (a credential in the logs); this
   * bean makes that auto-configuration back off and refuses every attempt.
   * https://docs.spring.io/spring-boot/reference/web/spring-security.html
   */
  @Bean
  AuthenticationManager noAuthentication() {
    return authentication -> {
      throw new ProviderNotFoundException("This service has no API and no users");
    };
  }
}
