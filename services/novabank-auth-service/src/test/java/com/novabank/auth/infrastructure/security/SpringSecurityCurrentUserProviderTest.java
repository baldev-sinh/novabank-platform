package com.novabank.auth.infrastructure.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.novabank.auth.application.security.JwtUser;
import com.novabank.auth.domain.model.RoleName;
import java.util.EnumSet;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

class SpringSecurityCurrentUserProviderTest {

    private static final UUID USER_ID =
        UUID.randomUUID();

    private static final String EMAIL =
        "baldev@example.com";

    private SpringSecurityCurrentUserProvider provider;

    @BeforeEach
    void setUp() {
        provider =
            new SpringSecurityCurrentUserProvider();

        SecurityContextHolder.clearContext();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("Should return authenticated JwtUser")
    void shouldReturnAuthenticatedJwtUser() {

        JwtUser user =
            new JwtUser(
                USER_ID,
                EMAIL,
                EnumSet.of(RoleName.CUSTOMER)
            );

        authenticate(user);

        JwtUser result =
            provider.getCurrentUser();

        assertThat(result)
            .isEqualTo(user);
    }

    @Test
    @DisplayName("Should reject when authentication is missing")
    void shouldRejectWhenAuthenticationIsMissing() {

        assertThatThrownBy(
            () -> provider.getCurrentUser()
        )
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("No authenticated user found");
    }

    @Test
    @DisplayName("Should reject unauthenticated request")
    void shouldRejectUnauthenticatedRequest() {

        UsernamePasswordAuthenticationToken authentication =
            new UsernamePasswordAuthenticationToken(
                null,
                null
            );

        SecurityContextHolder.getContext()
            .setAuthentication(authentication);

        assertThatThrownBy(
            () -> provider.getCurrentUser()
        )
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("No authenticated user found");
    }

    @Test
    @DisplayName("Should reject authentication with unexpected principal")
    void shouldRejectUnexpectedPrincipal() {

        UsernamePasswordAuthenticationToken authentication =
            new UsernamePasswordAuthenticationToken(
                "unexpected-principal",
                null,
                java.util.List.of()
            );

        SecurityContextHolder.getContext()
            .setAuthentication(authentication);

        assertThatThrownBy(
            () -> provider.getCurrentUser()
        )
            .isInstanceOf(IllegalStateException.class)
            .hasMessage(
                "Authenticated principal is not a JwtUser"
            );
    }

    private void authenticate(JwtUser user) {

        UsernamePasswordAuthenticationToken authentication =
            new UsernamePasswordAuthenticationToken(
                user,
                null,
                java.util.List.of()
            );

        SecurityContextHolder.getContext()
            .setAuthentication(authentication);
    }
}
