package com.novabank.auth.infrastructure.configuration;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import com.novabank.platform.security.ProblemAccessDeniedHandler;
import com.novabank.platform.security.ProblemAuthenticationEntryPoint;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.server.authorization.OAuth2Authorization;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.settings.AuthorizationServerSettings;
import org.springframework.security.oauth2.server.authorization.settings.ClientSettings;
import org.springframework.security.oauth2.server.authorization.settings.OAuth2TokenFormat;
import org.springframework.security.oauth2.server.authorization.settings.TokenSettings;
import org.springframework.security.oauth2.server.authorization.token.JwtEncodingContext;
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenCustomizer;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Service tokens (7.2, Decision AF): Spring Authorization Server's {@code client_credentials} grant
 * at {@code POST /oauth2/token}, clients authenticating with {@code private_key_jwt} (RFC 7523, as
 * RFC 9700 recommends: auth stores no client secrets). Tokens are signed with the same key as user
 * tokens and carry the same issuer and audience, so every service validates them unchanged; they
 * carry {@code scope} (e.g. {@code accounts:read}) instead of user roles.
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(ServiceClientsProperties.class)
public class AuthorizationServerConfiguration {

  /** Only the authorization server's own endpoints; every other request goes to the API chain. */
  @Bean
  @Order(1)
  SecurityFilterChain authorizationServerSecurityFilterChain(
      HttpSecurity http,
      ProblemAuthenticationEntryPoint entryPoint,
      ProblemAccessDeniedHandler accessDeniedHandler)
      throws Exception {
    http.oauth2AuthorizationServer(
            authorizationServer -> http.securityMatcher(authorizationServer.getEndpointsMatcher()))
        .authorizeHttpRequests(auth -> auth.anyRequest().authenticated())
        .sessionManagement(
            session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .exceptionHandling(exceptions ->
            exceptions
                .authenticationEntryPoint(entryPoint)
                .accessDeniedHandler(accessDeniedHandler));
    return http.build();
  }

  @Bean
  AuthorizationServerSettings authorizationServerSettings(JwtProperties jwt) {
    return AuthorizationServerSettings.builder().issuer(jwt.issuer()).build();
  }

  /** The authorization server signs with auth's existing key (same kid as user tokens). */
  @Bean
  JWKSource<SecurityContext> jwkSource(RSAKey jwtSigningKey) {
    return new ImmutableJWKSet<>(new JWKSet(jwtSigningKey));
  }

  @Bean
  RegisteredClientRepository registeredClientRepository(ServiceClientsProperties properties) {
    return new ConfiguredClients(
        properties.clients().stream()
            .map(client -> toRegisteredClient(client, properties))
            .collect(Collectors.toMap(RegisteredClient::getClientId, Function.identity())));
  }

  /**
   * Service tokens are for NovaBank APIs: same audience as user tokens, so validation is shared.
   */
  @Bean
  OAuth2TokenCustomizer<JwtEncodingContext> serviceTokenCustomizer(JwtProperties jwt) {
    return context -> {
      if (OAuth2TokenType.ACCESS_TOKEN.equals(context.getTokenType())
          && AuthorizationGrantType.CLIENT_CREDENTIALS.equals(
              context.getAuthorizationGrantType())) {
        context.getClaims().audience(List.of(jwt.audience()));
      }
    };
  }

  /**
   * Nothing to store: service tokens are short-lived, self-contained JWTs that services validate
   * locally, and there are no refresh tokens or authorization codes. The default in-memory store
   * would grow without bound and differ per instance. Consequence: no introspection or revocation
   * for service tokens; their 5-minute lifetime bounds the risk.
   */
  @Bean
  OAuth2AuthorizationService authorizationService() {
    return new OAuth2AuthorizationService() {
      @Override
      public void save(OAuth2Authorization authorization) {}

      @Override
      public void remove(OAuth2Authorization authorization) {}

      @Override
      public OAuth2Authorization findById(String id) {
        return null;
      }

      @Override
      public OAuth2Authorization findByToken(String token, OAuth2TokenType tokenType) {
        return null;
      }
    };
  }

  private static RegisteredClient toRegisteredClient(
      ServiceClientsProperties.Client client, ServiceClientsProperties properties) {
    return RegisteredClient.withId(
            UUID.nameUUIDFromBytes(client.clientId().getBytes(StandardCharsets.UTF_8)).toString())
        .clientId(client.clientId())
        .clientAuthenticationMethod(ClientAuthenticationMethod.PRIVATE_KEY_JWT)
        .authorizationGrantType(AuthorizationGrantType.CLIENT_CREDENTIALS)
        .scopes(scopes -> scopes.addAll(client.scopes()))
        .clientSettings(
            ClientSettings.builder()
                .jwkSetUrl(client.jwkSetUri())
                .tokenEndpointAuthenticationSigningAlgorithm(SignatureAlgorithm.RS256)
                .build())
        .tokenSettings(
            TokenSettings.builder()
                .accessTokenTimeToLive(properties.tokenTtl())
                .accessTokenFormat(OAuth2TokenFormat.SELF_CONTAINED)
                .build())
        .build();
  }

  /** Read-only: clients come from configuration, not from a registration endpoint. */
  record ConfiguredClients(Map<String, RegisteredClient> byClientId)
      implements RegisteredClientRepository {

    @Override
    public void save(RegisteredClient registeredClient) {
      throw new UnsupportedOperationException("Service clients are configured, not registered");
    }

    @Override
    public RegisteredClient findById(String id) {
      return byClientId.values().stream()
          .filter(client -> client.getId().equals(id))
          .findFirst()
          .orElse(null);
    }

    @Override
    public RegisteredClient findByClientId(String clientId) {
      return byClientId.get(clientId);
    }
  }
}
