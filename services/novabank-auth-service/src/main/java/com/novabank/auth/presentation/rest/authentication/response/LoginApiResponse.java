package com.novabank.auth.presentation.rest.authentication.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

public record LoginApiResponse(

    @Schema(
        description = "JWT access token",
        example = "eyJhbGciOiJIUzI1NiJ9..."
    )
    String accessToken,

    @Schema(
        description = "Token type",
        example = "Bearer"
    )
    String tokenType,

    @Schema(
        description = "Access token lifetime in seconds",
        example = "900"
    )
    long expiresIn

) {
}
