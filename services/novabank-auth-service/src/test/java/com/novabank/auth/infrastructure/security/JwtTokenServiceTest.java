package com.novabank.auth.infrastructure.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.novabank.auth.application.security.JwtUser;
import com.novabank.auth.domain.model.RoleName;
import com.novabank.auth.infrastructure.configuration.JwtProperties;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.util.EnumSet;
import java.util.UUID;
import javax.crypto.SecretKey;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class JwtTokenServiceTest {

    private static final UUID USER_ID =
        UUID.randomUUID();

    private static final String EMAIL =
        "baldev@example.com";

    private static final String SECRET =
        "this-is-a-super-secret-key-for-novabank-auth-service-123456";

    private static final String ISSUER =
        "novabank-auth-service";

    private static final long EXPIRATION = 900;

    private JwtTokenService service;

    @BeforeEach
    void setUp() {

        JwtProperties properties =
            new JwtProperties(
                SECRET,
                EXPIRATION,
                ISSUER
            );

        service = new JwtTokenService(properties);
    }

    @Test
    @DisplayName("Should generate access token")
    void shouldGenerateAccessToken() {

        String token =
            service.generateAccessToken(createUser());

        assertThat(token).isNotBlank();
    }

    @Test
    @DisplayName("Should validate generated token")
    void shouldValidateGeneratedToken() {

        String token =
            service.generateAccessToken(createUser());

        assertThat(service.validate(token))
            .isTrue();
    }

    @Test
    @DisplayName("Should reject malformed token")
    void shouldRejectMalformedToken() {

        assertThat(service.validate("abc"))
            .isFalse();
    }

    @Test
    @DisplayName("Should reject null token")
    void shouldRejectNullToken() {

        assertThatThrownBy(() ->
            service.validate(null))
            .isInstanceOf(
                NullPointerException.class)
            .hasMessage(
                "Token cannot be null");
    }

    @Test
    @DisplayName("Should reject token with invalid signature")
    void shouldRejectTokenWithInvalidSignature() {

        String token =
            service.generateAccessToken(createUser());

        // Corrupt the signature by changing the last character
        token = token.substring(0, token.length() - 1) + "A";

        assertThat(service.validate(token))
            .isFalse();
    }

    @Test
    @DisplayName("Should parse subject")
    void shouldParseSubject() {

        String token =
            service.generateAccessToken(createUser());

        JwtUser jwtUser =
            service.parse(token);

        assertThat(jwtUser.email())
            .isEqualTo(EMAIL);
    }

    @Test
    @DisplayName("Should parse user id")
    void shouldParseUserId() {

        String token =
            service.generateAccessToken(createUser());

        JwtUser jwtUser =
            service.parse(token);

        assertThat(jwtUser.userId())
            .isEqualTo(USER_ID);
    }

    @Test
    @DisplayName("Should parse roles")
    void shouldParseRoles() {

        String token =
            service.generateAccessToken(createUser());

        JwtUser jwtUser =
            service.parse(token);

        assertThat(jwtUser.roles())
            .containsExactly(RoleName.CUSTOMER);
    }

    @Test
    @DisplayName("Should reject null token when parsing")
    void shouldRejectNullTokenWhenParsing() {

        assertThatThrownBy(() ->
            service.parse(null))
            .isInstanceOf(
                NullPointerException.class)
            .hasMessage("Token cannot be null");
    }

    @Test
    @DisplayName("Should return configured access token expiration")
    void shouldReturnConfiguredAccessTokenExpiration() {

        assertThat(service.accessTokenExpiration())
            .isEqualTo(EXPIRATION);
    }

    @Test
    @DisplayName("Should preserve all roles")
    void shouldPreserveAllRoles() {

        JwtUser user =
            new JwtUser(
                USER_ID,
                EMAIL,
                EnumSet.of(
                    RoleName.CUSTOMER,
                    RoleName.ADMIN
                )
            );

        String token =
            service.generateAccessToken(user);

        JwtUser parsed =
            service.parse(token);

        assertThat(parsed.roles())
            .containsExactlyInAnyOrder(
                RoleName.CUSTOMER,
                RoleName.ADMIN
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

    @Test
    @DisplayName("Should reject expired token")
    void shouldRejectExpiredToken() {

        JwtProperties expiredProperties =
            new JwtProperties(
                SECRET,
                -1,
                ISSUER
            );

        JwtTokenService expiredService =
            new JwtTokenService(expiredProperties);

        String token =
            expiredService.generateAccessToken(createUser());

        assertThat(expiredService.validate(token))
            .isFalse();
    }

    private JwtUser createUser() {

        return new JwtUser(
            USER_ID,
            EMAIL,
            EnumSet.of(RoleName.CUSTOMER)
        );
    }

}
