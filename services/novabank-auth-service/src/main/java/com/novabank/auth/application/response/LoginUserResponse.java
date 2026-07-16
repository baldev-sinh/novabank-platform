package com.novabank.auth.application.response;

public record LoginUserResponse(

    String accessToken,
    String tokenType,
    long expiresIn
) {
}
