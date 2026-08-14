package com.novabank.auth.infrastructure.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.novabank.auth.application.port.security.TokenService;
import com.novabank.auth.application.security.JwtUser;
import com.novabank.auth.domain.model.RoleName;
import jakarta.servlet.FilterChain;
import java.util.EnumSet;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

@ExtendWith(MockitoExtension.class)
class JwtAuthenticationFilterTest {

    @Mock
    private TokenService tokenService;

    private JwtAuthenticationFilter filter;

    @BeforeEach
    void setUp() {
        filter = new JwtAuthenticationFilter(tokenService);
        SecurityContextHolder.clearContext();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("Should continue request when authorization header is missing")
    void shouldContinueWhenAuthorizationHeaderIsMissing() throws Exception {

        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain filterChain = new MockFilterChain();

        filter.doFilter(request, response, filterChain);

        verify(tokenService, never()).validate(org.mockito.ArgumentMatchers.any());

        assertThat(SecurityContextHolder.getContext().getAuthentication())
            .isNull();

        assertThat(filterChain.getRequest())
            .isSameAs(request);
    }

    @Test
    @DisplayName("Should continue request when authorization header is not Bearer")
    void shouldContinueWhenAuthorizationHeaderIsNotBearer() throws Exception {

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(HttpHeaders.AUTHORIZATION, "Basic abc123");

        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain filterChain = new MockFilterChain();

        filter.doFilter(request, response, filterChain);

        verify(tokenService, never()).validate(org.mockito.ArgumentMatchers.any());

        assertThat(SecurityContextHolder.getContext().getAuthentication())
            .isNull();
    }

    @Test
    @DisplayName("Should continue request when Bearer token is blank")
    void shouldContinueWhenBearerTokenIsBlank() throws Exception {

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer ");

        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain filterChain = new MockFilterChain();

        filter.doFilter(request, response, filterChain);

        verify(tokenService, never()).validate(org.mockito.ArgumentMatchers.any());

        assertThat(SecurityContextHolder.getContext().getAuthentication())
            .isNull();
    }

    @Test
    @DisplayName("Should continue request when token is invalid")
    void shouldContinueWhenTokenIsInvalid() throws Exception {

        String token = "invalid-token";

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(
            HttpHeaders.AUTHORIZATION,
            "Bearer " + token
        );

        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain filterChain = new MockFilterChain();

        when(tokenService.validate(token))
            .thenReturn(false);

        filter.doFilter(request, response, filterChain);

        verify(tokenService).validate(token);
        verify(tokenService, never()).parse(token);

        assertThat(SecurityContextHolder.getContext().getAuthentication())
            .isNull();
    }

    @Test
    @DisplayName("Should authenticate request when token is valid")
    void shouldAuthenticateWhenTokenIsValid() throws Exception {

        String token = "valid-token";

        JwtUser jwtUser = new JwtUser(
            UUID.randomUUID(),
            "baldev@example.com",
            EnumSet.of(
                RoleName.CUSTOMER,
                RoleName.ADMIN
            )
        );

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(
            HttpHeaders.AUTHORIZATION,
            "Bearer " + token
        );

        MockHttpServletResponse response =
            new MockHttpServletResponse();

        AtomicReference<Authentication> authenticationReference =
            new AtomicReference<>();

        FilterChain filterChain =
            (req, res) ->
                authenticationReference.set(
                    SecurityContextHolder
                        .getContext()
                        .getAuthentication()
                );

        when(tokenService.validate(token))
            .thenReturn(true);

        when(tokenService.parse(token))
            .thenReturn(jwtUser);

        filter.doFilter(
            request,
            response,
            filterChain
        );

        Authentication authentication =
            authenticationReference.get();

        assertThat(authentication)
            .isNotNull();

        assertThat(authentication.getPrincipal())
            .isEqualTo(jwtUser);

        assertThat(authentication.isAuthenticated())
            .isTrue();

        assertThat(authentication.getAuthorities())
            .extracting("authority")
            .containsExactlyInAnyOrder(
                "ROLE_CUSTOMER",
                "ROLE_ADMIN"
            );

        verify(tokenService)
            .validate(token);

        verify(tokenService)
            .parse(token);

        assertThat(
            SecurityContextHolder
                .getContext()
                .getAuthentication()
        ).isNull();
    }

    @Test
    @DisplayName("Should not replace existing authentication")
    void shouldNotReplaceExistingAuthentication() throws Exception {

        Authentication existing =
            org.springframework.security.authentication.UsernamePasswordAuthenticationToken
                .authenticated(
                    "existing-user",
                    null,
                    java.util.List.of()
                );

        SecurityContextHolder.getContext()
            .setAuthentication(existing);

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(
            HttpHeaders.AUTHORIZATION,
            "Bearer valid-token"
        );

        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain filterChain = new MockFilterChain();

        filter.doFilter(request, response, filterChain);

        assertThat(
            SecurityContextHolder
                .getContext()
                .getAuthentication()
        )
            .isSameAs(existing);

        verify(tokenService, never()).validate(
            org.mockito.ArgumentMatchers.any()
        );
    }
}
