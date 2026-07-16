package com.novabank.auth.infrastructure.security;

import com.novabank.auth.application.port.security.TokenService;
import com.novabank.auth.application.security.JwtUser;
import com.novabank.auth.infrastructure.configuration.JwtProperties;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import javax.crypto.SecretKey;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class JwtTokenService implements TokenService {

    private final JwtProperties properties;

    @Override
    public String generateAccessToken(JwtUser user) {
        Objects.requireNonNull(user, "JwtUser cannot be null");

        Instant now = Instant.now();
        Instant expiration = now.plusSeconds(properties.expiration());

        List<String> roles =
            user.roles()
                .stream()
                .map(Enum::name)
                .toList();

        return Jwts.builder()
            .subject(user.email())
            .issuer(properties.issuer())
            .issuedAt(Date.from(now))
            .expiration(Date.from(expiration))
            .claim("userId", user.userId().toString())
            .claim("roles", roles)
            .signWith(signingKey())
            .compact();
    }

    private SecretKey signingKey(){
        Objects.requireNonNull(properties.secret(), "JWT secret cannot be null");

        return Keys.hmacShaKeyFor(
            properties.secret()
                .getBytes(StandardCharsets.UTF_8)
        );
    }
}
