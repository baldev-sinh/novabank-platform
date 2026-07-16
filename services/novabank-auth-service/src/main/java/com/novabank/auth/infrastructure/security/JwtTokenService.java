package com.novabank.auth.infrastructure.security;

import com.novabank.auth.application.port.security.TokenService;
import com.novabank.auth.application.security.JwtUser;
import com.novabank.auth.domain.model.RoleName;
import com.novabank.auth.infrastructure.configuration.JwtProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
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
        long expirationSeconds = properties.expiration();

        Instant expiration =
            now.plusSeconds(expirationSeconds);

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

    @Override
    public boolean validate(String token) {
        try {
            parseClaims(token);
            return true;
        }
        catch (JwtException | IllegalArgumentException ex) {
            return false;
        }
    }

    @Override
    public JwtUser parse(String token) {

        Claims claims = parseClaims(token);

        UUID userId = Objects.requireNonNull(
            UUID.fromString(claims.get("userId", String.class)),
            "JWT userId is missing"
        );

        String email =
            Objects.requireNonNull(
                claims.getSubject(),
                "JWT subject is missing"
            );

        EnumSet<RoleName> roles = parseRoles(claims);

        return new JwtUser(
            userId,
            email,
            roles
        );
    }

    private static EnumSet<RoleName> parseRoles(Claims claims) {
        Object rolesClaim = claims.get("roles");

        if (!(rolesClaim instanceof List<?> rawRoles)) {
            throw new JwtException("Invalid roles claim");
        }

        EnumSet<RoleName> roles =
            rawRoles.stream()
                .map(String.class::cast)
                .map(RoleName::valueOf)
                .collect(
                    () -> EnumSet.noneOf(RoleName.class),
                    EnumSet::add,
                    EnumSet::addAll
                );
        return roles;
    }

    @Override
    public long accessTokenExpiration() {
        return properties.expiration();
    }

    private SecretKey signingKey(){
        Objects.requireNonNull(properties.secret(), "JWT secret cannot be null");

        return Keys.hmacShaKeyFor(
            properties.secret()
                .getBytes(StandardCharsets.UTF_8)
        );
    }

    private Claims parseClaims(String token){
        Objects.requireNonNull(token, "Token cannot be null");

        return Jwts.parser()
            .verifyWith(signingKey())
            .build()
            .parseSignedClaims(token)
            .getPayload();
    }
}
