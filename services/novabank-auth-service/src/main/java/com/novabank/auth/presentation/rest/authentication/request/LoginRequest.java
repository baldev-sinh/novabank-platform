package com.novabank.auth.presentation.rest.authentication.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record LoginRequest(

    @Schema(
        description = "User email address",
        example = "baldev@example.com"
    )
    @NotBlank(message = "Email is required")
    @Email(message = "Invalid email address")
    String email,


    @Schema(
        description = "User password",
        example = "Password@123"
    )
    @NotBlank(message = "Password is required")
    String password

) {
}
