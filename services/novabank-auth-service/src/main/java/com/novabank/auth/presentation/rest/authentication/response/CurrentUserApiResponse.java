package com.novabank.auth.presentation.rest.authentication.response;


import com.novabank.auth.domain.model.RoleName;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Set;
import java.util.UUID;

public record CurrentUserApiResponse(

    @Schema(
        description = "Authenticated user's unique identifier",
        example = "123e4567-e89b-12d3-a456-426614174000"
    )
    UUID userId,

    @Schema(
        description = "Authenticated user's email",
        example = "baldev@example.com"
    )
    String email,

    @Schema(
        description = "Authenticated user's roles",
        example = "[\"CUSTOMER\"]"
    )
    Set<RoleName> roles

) {
}
