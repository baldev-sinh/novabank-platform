package com.novabank.auth.presentation.rest.authentication.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

public record LoginApiResponse(

    @Schema(example = "123e4567-e89b-12d3-a456-426614174000")
    UUID userId,

    @Schema(example = "baldev@example.com")
    String email,

    @Schema(example = "PENDING_VERIFICATION")
    String status

) {
}
