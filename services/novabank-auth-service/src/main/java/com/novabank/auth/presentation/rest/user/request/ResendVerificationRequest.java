package com.novabank.auth.presentation.rest.user.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ResendVerificationRequest(
    @Schema(description = "Email address of the unverified account", example = "baldev@example.com")
        @NotBlank(message = "Email is required") @Email(message = "Invalid email address") @Size(max = 255, message = "Email must be at most 255 characters") String email) {}
