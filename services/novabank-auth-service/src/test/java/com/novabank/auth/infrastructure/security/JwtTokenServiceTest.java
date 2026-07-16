package com.novabank.auth.infrastructure.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.novabank.auth.application.security.JwtUser;
import com.novabank.auth.domain.model.RoleName;
import com.novabank.auth.infrastructure.configuration.JwtProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import javax.crypto.SecretKey;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class JwtTokenServiceTest {

    private static final UUID USER_ID = UUID.randomUUID();
    private static final String EMAIL = "baldev@example.com";

    /**
     * Must be at least 32 bytes for HS256.
     */
    private static final String SECRET =
        "this-is-a-super-secret-key-for-novabank-auth-service-123456";

    private static final long EXPIRATION = 900L;

    private static final String ISSUER =
        "novabank-auth-service";

    private JwtTokenService service;

    private JwtProperties properties;

    @BeforeEach
    void setUp() {

        properties = new JwtProperties(
            SECRET,
            EXPIRATION,
            ISSUER
        );

        service = new JwtTokenService(properties);
    }

    @Test
    @DisplayName("Should generate JWT access token")
    void shouldGenerateAccessToken() {

        String token =
            service.generateAccessToken(createUser());

        assertThat(token).isNotBlank();
    }

    @Test
    @DisplayName("Should contain subject")
    void shouldContainSubject() {

        String token =
            service.generateAccessToken(createUser());

        Claims claims = parseClaims(token);

        assertThat(claims.getSubject())
            .isEqualTo(EMAIL);
    }

    @Test
    @DisplayName("Should contain issuer")
    void shouldContainIssuer() {

        String token =
            service.generateAccessToken(createUser());

        Claims claims = parseClaims(token);

        assertThat(claims.getIssuer())
            .isEqualTo(ISSUER);
    }

    @Test
    @DisplayName("Should contain user id claim")
    void shouldContainUserIdClaim() {

        String token =
            service.generateAccessToken(createUser());

        Claims claims = parseClaims(token);

        assertThat(
            claims.get("userId", String.class)
        ).isEqualTo(USER_ID.toString());
    }

    @Test
    @DisplayName("Should contain roles")
    void shouldContainRoles() {

        String token =
            service.generateAccessToken(createUser());

        Claims claims = parseClaims(token);

        List<String> roles =
            claims.get("roles", List.class);

        assertThat(roles)
            .containsExactly("CUSTOMER");
    }

    @Test
    @DisplayName("Should contain issued at")
    void shouldContainIssuedAt() {

        String token =
            service.generateAccessToken(createUser());

        Claims claims = parseClaims(token);

        assertThat(claims.getIssuedAt())
            .isNotNull();
    }

    @Test
    @DisplayName("Should contain expiration")
    void shouldContainExpiration() {

        String token =
            service.generateAccessToken(createUser());

        Claims claims = parseClaims(token);

        assertThat(claims.getExpiration())
            .isAfter(new Date());
    }

    @Test
    @DisplayName("Should contain all user roles")
    void shouldContainAllRoles() {

        JwtUser user =
            new JwtUser(
                USER_ID,
                EMAIL,
                Set.of(
                    RoleName.CUSTOMER,
                    RoleName.ADMIN
                )
            );

        String token =
            service.generateAccessToken(user);

        Claims claims = parseClaims(token);

        List<String> roles =
            claims.get("roles", List.class);

        assertThat(roles)
            .containsExactlyInAnyOrder(
                "CUSTOMER",
                "ADMIN"
            );
    }

    @Test
    @DisplayName("Should reject null user")
    void shouldRejectNullUser() {

        assertThatThrownBy(() ->
            service.generateAccessToken(null))
            .isInstanceOf(NullPointerException.class)
            .hasMessage("JwtUser cannot be null");
    }


    private JwtUser createUser() {

        return new JwtUser(
            USER_ID,
            EMAIL,
            EnumSet.of(RoleName.CUSTOMER)
        );
    }

    private Claims parseClaims(String token) {

        return Jwts.parser()
            .verifyWith(signingKey())
            .build()
            .parseSignedClaims(token)
            .getPayload();
    }

    private SecretKey signingKey() {

        return Keys.hmacShaKeyFor(
            SECRET.getBytes(StandardCharsets.UTF_8)
        );
    }

}
