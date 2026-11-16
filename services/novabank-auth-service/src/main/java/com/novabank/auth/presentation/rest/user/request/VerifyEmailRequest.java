package com.novabank.auth.presentation.rest.user.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record VerifyEmailRequest(
    @Schema(description = "Token from the verification email link")
        @NotBlank(message = "Token is required") @Size(max = 128, message = "Token is too long") String token) {}
