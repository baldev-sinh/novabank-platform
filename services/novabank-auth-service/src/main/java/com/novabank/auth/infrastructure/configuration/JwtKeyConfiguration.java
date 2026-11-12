package com.novabank.auth.infrastructure.configuration;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
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
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.core.io.Resource;
import org.springframework.security.converter.RsaKeyConverters;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimNames;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtIssuerValidator;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.util.StringUtils;

/** Builds the RSA signing key and the JWT encoder/decoder used by this service. */
@Configuration(proxyBeanMethods = false)
public class JwtKeyConfiguration {

  private static final Logger LOGGER = LoggerFactory.getLogger(JwtKeyConfiguration.class);

  @Bean
  public RSAKey jwtSigningKey(JwtProperties properties) {
    KeyPair keyPair = loadOrGenerateKeyPair(properties);
    RSAPublicKey publicKey = (RSAPublicKey) keyPair.getPublic();
    try {
      RSAKey key =
          new RSAKey.Builder(publicKey).privateKey((RSAPrivateKey) keyPair.getPrivate()).build();
      // kid = RFC 7638 thumbprint: the same key always gets the same id, on every instance.
      return new RSAKey.Builder(key).keyID(key.computeThumbprint().toString()).build();
    } catch (JOSEException ex) {
      throw new IllegalStateException("Could not compute the JWT key id", ex);
    }
  }

  @Bean
  public JwtEncoder jwtEncoder(RSAKey jwtSigningKey) {
    return new NimbusJwtEncoder(new ImmutableJWKSet<>(new JWKSet(jwtSigningKey)));
  }

  @Bean
  public JwtDecoder jwtDecoder(RSAKey jwtSigningKey, JwtProperties properties)
      throws JOSEException {
    NimbusJwtDecoder decoder =
        NimbusJwtDecoder.withPublicKey(jwtSigningKey.toRSAPublicKey()).build();
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

  private static KeyPair loadOrGenerateKeyPair(JwtProperties properties) {
    boolean configured =
        StringUtils.hasText(properties.privateKeyLocation())
            && StringUtils.hasText(properties.publicKeyLocation());
    if (configured) {
      return new KeyPair(
          read(properties.publicKeyLocation(), RsaKeyConverters.x509()::convert),
          read(properties.privateKeyLocation(), RsaKeyConverters.pkcs8()::convert));
    }
    if (properties.generateEphemeralKey()) {
      LOGGER.warn(
          "Using an ephemeral RSA key for JWT signing. Tokens stop working after a restart. "
              + "Never use this outside local development.");
      return generateKeyPair();
    }
    throw new IllegalStateException(
        "No JWT signing key configured. Set security.jwt.private-key-location and "
            + "security.jwt.public-key-location (or security.jwt.generate-ephemeral-key=true "
            + "for local development).");
  }

  private static <T> T read(String location, PemReader<T> reader) {
    Resource resource = new DefaultResourceLoader().getResource(location);
    try (InputStream in = resource.getInputStream()) {
      return reader.read(in);
    } catch (IOException ex) {
      throw new IllegalStateException("Could not read JWT key from " + location, ex);
    }
  }

  private static KeyPair generateKeyPair() {
    try {
      KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
      generator.initialize(2048);
      return generator.generateKeyPair();
    } catch (NoSuchAlgorithmException ex) {
      throw new IllegalStateException("RSA is not available in this JVM", ex);
    }
  }

  @FunctionalInterface
  private interface PemReader<T> {
    T read(InputStream in);
  }
}
