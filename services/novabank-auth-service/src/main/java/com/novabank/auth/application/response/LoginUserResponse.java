package com.novabank.auth.application.response;

import com.novabank.auth.application.service.RefreshTokenIssuer.IssuedRefreshToken;

/**
 * @param refreshToken goes into the HttpOnly cookie, never into a response body
 */
public record LoginUserResponse(
    String accessToken, String tokenType, long expiresIn, IssuedRefreshToken refreshToken) {}
