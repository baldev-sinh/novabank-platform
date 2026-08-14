package com.novabank.auth.infrastructure.security;

import com.novabank.auth.application.port.security.TokenService;
import com.novabank.auth.application.security.JwtUser;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Authenticates incoming requests using a JWT Bearer token.
 * <p>
 * If a valid JWT is present, the authenticated user is stored in the
 * Spring SecurityContext for the duration of the request.
 */

@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";
    private static final String ROLE_PREFIX = "ROLE_";

    private final TokenService tokenService;

    @Override
    protected void doFilterInternal(
        HttpServletRequest request,
        HttpServletResponse response,
        FilterChain filterChain
    ) throws ServletException, IOException {

        String authorizationHeader = request.getHeader(HttpHeaders.AUTHORIZATION);

        // No Authorization header
        if (authorizationHeader == null || authorizationHeader.isBlank()) {
            filterChain.doFilter(request, response);
            return;
        }

        // Not a Bearer token
        if (!authorizationHeader.startsWith(BEARER_PREFIX)) {
            filterChain.doFilter(request, response);
            return;
        }

        // Already authenticated
        if (SecurityContextHolder.getContext().getAuthentication() != null) {
            filterChain.doFilter(request, response);
            return;
        }

        String token = authorizationHeader.substring(BEARER_PREFIX.length()).trim();

        if(token.isEmpty()){
            filterChain.doFilter(request, response);
            return;
        }

        try {
            if (!tokenService.validate(token)) {
                filterChain.doFilter(request, response);
                return;
            }

            JwtUser jwtUser = tokenService.parse(token);

            List<SimpleGrantedAuthority> authorities =
                jwtUser.roles()
                    .stream()
                    .map(role ->
                        new SimpleGrantedAuthority(
                            ROLE_PREFIX + role.name()
                        )
                    )
                    .toList();

            UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(
                    jwtUser,
                    null,
                    authorities
                );

            authentication.setDetails(
                new WebAuthenticationDetailsSource()
                    .buildDetails(request)
            );

            SecurityContextHolder.getContext()
                .setAuthentication(authentication);

            filterChain.doFilter(request, response);
        }
        catch (JwtException | IllegalArgumentException ex) {
            filterChain.doFilter(request, response);
        }
        finally {
            SecurityContextHolder.clearContext();
        }

    }
}
