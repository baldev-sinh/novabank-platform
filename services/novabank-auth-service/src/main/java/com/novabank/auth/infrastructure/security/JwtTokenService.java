package com.novabank.auth.infrastructure.security;

import com.nimbusds.jose.jwk.RSAKey;
import com.novabank.auth.application.port.security.TokenService;
import com.novabank.auth.application.security.AuthenticatedUser;
import com.novabank.auth.infrastructure.configuration.JwtProperties;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

/** Issues RS256-signed access tokens. Claim names here must match {@link JwtClaims}. */
@Service
public class JwtTokenService implements TokenService {

  private final JwtEncoder encoder;
  private final JwtProperties properties;
  private final String keyId;
  private final Clock clock;

  public JwtTokenService(
      JwtEncoder encoder, JwtProperties properties, RSAKey jwtSigningKey, Clock clock) {
    this.encoder = encoder;
    this.properties = properties;
    this.keyId = jwtSigningKey.getKeyID();
    this.clock = clock;
  }

  @Override
  public String generateAccessToken(AuthenticatedUser user) {
    Objects.requireNonNull(user, "user cannot be null");
    Instant now = clock.instant();

    JwtClaimsSet claims =
        JwtClaimsSet.builder()
            .issuer(properties.issuer())
            .audience(List.of(properties.audience()))
            // sub is the stable user id, not the email: emails can change, ids never do.
            .subject(user.userId().toString())
            .id(UUID.randomUUID().toString())
            .issuedAt(now)
            .expiresAt(now.plus(properties.accessTokenTtl()))
            .claim(JwtClaims.EMAIL, user.email())
            .claim(JwtClaims.ROLES, user.roles().stream().map(Enum::name).sorted().toList())
            .build();

    JwsHeader header = JwsHeader.with(SignatureAlgorithm.RS256).keyId(keyId).build();
    return encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
  }

  @Override
  public long accessTokenExpiration() {
    return properties.accessTokenTtl().toSeconds();
  }
}
