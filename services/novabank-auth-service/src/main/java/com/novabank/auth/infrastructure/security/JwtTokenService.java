package com.novabank.auth.infrastructure.security;

import com.novabank.auth.application.port.security.TokenService;
import com.novabank.auth.application.security.JwtUser;
import com.novabank.auth.infrastructure.configuration.JwtProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class JwtTokenService implements TokenService {

    private final JwtProperties properties;

    @Override
    public String generateAccessToken(JwtUser user) {
        throw new UnsupportedOperationException(
            "Not implemented yet."
        );
    }
}
