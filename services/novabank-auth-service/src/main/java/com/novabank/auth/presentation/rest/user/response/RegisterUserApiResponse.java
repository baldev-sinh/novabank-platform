package com.novabank.auth.presentation.rest.user.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

public record RegisterUserApiResponse(

    @Schema(example = "f798236hdfoa238hdfa298")
    UUID userId,

    @Schema(example = "baldev@example.com")
    String email,

    @Schema(example = "PENDING_VERIFICATION")
    String status

) {
}
