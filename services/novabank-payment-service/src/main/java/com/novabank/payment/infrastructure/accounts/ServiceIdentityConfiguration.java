package com.novabank.payment.infrastructure.accounts;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.jwk.RSAKey;
import java.io.IOException;
import java.io.InputStream;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.core.io.Resource;
import org.springframework.security.converter.RsaKeyConverters;
import org.springframework.security.oauth2.client.AuthorizedClientServiceOAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.ClientCredentialsOAuth2AuthorizedClientProvider;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.endpoint.NimbusJwtClientAuthenticationParametersConverter;
import org.springframework.security.oauth2.client.endpoint.OAuth2ClientCredentialsGrantRequest;
import org.springframework.security.oauth2.client.endpoint.RestClientClientCredentialsTokenResponseClient;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.util.StringUtils;

/**
 * This service's identity towards auth (7.2/7.3, Decision AF): an RSA key whose public half is
 * published at {@code /.well-known/jwks.json}, and a client that gets service tokens with {@code
 * client_credentials}, authenticating with a JWT signed by that key ({@code private_key_jwt}). No
 * client secret exists anywhere.
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(ServiceIdentityConfiguration.ServiceIdentityProperties.class)
public class ServiceIdentityConfiguration {

  private static final Logger LOGGER = LoggerFactory.getLogger(ServiceIdentityConfiguration.class);

  /**
   * {@code novabank.service-identity.*}.
   *
   * @param privateKeyLocation PKCS#8 PEM, e.g. {@code file:/run/secrets/payment-service.pem}
   * @param publicKeyLocation X.509 PEM matching the private key
   * @param generateEphemeralKey a throwaway key per start (local, compose, tests): auth fetches the
   *     public key from this service's JWKS, so a fresh key just works
   * @param authIssuer auth's issuer; the client assertion names it as audience
   */
  @ConfigurationProperties(prefix = "novabank.service-identity")
  public record ServiceIdentityProperties(
      String privateKeyLocation,
      String publicKeyLocation,
      boolean generateEphemeralKey,
      String authIssuer) {}

  @Bean
  RSAKey serviceSigningKey(ServiceIdentityProperties properties) {
    KeyPair keyPair = loadOrGenerate(properties);
    try {
      RSAKey key =
          new RSAKey.Builder((RSAPublicKey) keyPair.getPublic())
              .privateKey((RSAPrivateKey) keyPair.getPrivate())
              .build();
      // kid = RFC 7638 thumbprint: stable for the same key on every instance.
      return new RSAKey.Builder(key).keyID(key.computeThumbprint().toString()).build();
    } catch (JOSEException ex) {
      throw new IllegalStateException("Could not compute the service key id", ex);
    }
  }

  /**
   * Caches the service token until shortly before it expires, then fetches a new one. The client
   * assertion's audience is auth's issuer as well as the token endpoint: in compose the two differ
   * (localhost vs the container name), and auth accepts either.
   */
  @Bean
  OAuth2AuthorizedClientManager serviceTokenManager(
      ClientRegistrationRepository registrations,
      OAuth2AuthorizedClientService authorizedClients,
      RSAKey serviceSigningKey,
      ServiceIdentityProperties properties) {
    NimbusJwtClientAuthenticationParametersConverter<OAuth2ClientCredentialsGrantRequest>
        assertion =
            new NimbusJwtClientAuthenticationParametersConverter<>(
                registration -> serviceSigningKey);
    assertion.setJwtClientAssertionCustomizer(context -> {
      String tokenUri =
          context
              .getAuthorizationGrantRequest()
              .getClientRegistration()
              .getProviderDetails()
              .getTokenUri();
      context
          .getClaims()
          .audience(
              StringUtils.hasText(properties.authIssuer())
                  ? List.of(properties.authIssuer(), tokenUri)
                  : List.of(tokenUri));
    });
    RestClientClientCredentialsTokenResponseClient tokenClient =
        new RestClientClientCredentialsTokenResponseClient();
    tokenClient.addParametersConverter(assertion);

    ClientCredentialsOAuth2AuthorizedClientProvider provider =
        new ClientCredentialsOAuth2AuthorizedClientProvider();
    provider.setAccessTokenResponseClient(tokenClient);

    AuthorizedClientServiceOAuth2AuthorizedClientManager manager =
        new AuthorizedClientServiceOAuth2AuthorizedClientManager(registrations, authorizedClients);
    manager.setAuthorizedClientProvider(provider);
    return manager;
  }

  private static KeyPair loadOrGenerate(ServiceIdentityProperties properties) {
    if (StringUtils.hasText(properties.privateKeyLocation())
        && StringUtils.hasText(properties.publicKeyLocation())) {
      return new KeyPair(
          read(properties.publicKeyLocation(), RsaKeyConverters.x509()::convert),
          read(properties.privateKeyLocation(), RsaKeyConverters.pkcs8()::convert));
    }
    if (properties.generateEphemeralKey()) {
      LOGGER.warn(
          "Using an ephemeral key for this service's identity. Fine locally; real environments"
              + " mount novabank.service-identity.private-key-location from the secret store.");
      try {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        return generator.generateKeyPair();
      } catch (NoSuchAlgorithmException ex) {
        throw new IllegalStateException("RSA is not available in this JVM", ex);
      }
    }
    throw new IllegalStateException(
        "No service identity key configured. Set novabank.service-identity.private-key-location"
            + " and public-key-location (or generate-ephemeral-key=true locally).");
  }

  private static <T> T read(String location, PemReader<T> reader) {
    Resource resource = new DefaultResourceLoader().getResource(location);
    try (InputStream in = resource.getInputStream()) {
      return reader.read(in);
    } catch (IOException ex) {
      throw new IllegalStateException("Could not read the service key from " + location, ex);
    }
  }

  @FunctionalInterface
  private interface PemReader<T> {
    T read(InputStream in);
  }
}
