package com.novabank.auth.presentation.rest.user.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** The full password rules (incl. BCrypt's 72-byte limit) live in {@code PasswordPolicy}. */
public record RegisterUserRequest(
    @Schema(description = "User email address", example = "baldev@example.com")
        @NotBlank(message = "Email is required") @Email(message = "Invalid email address") @Size(max = 255, message = "Email must be at most 255 characters") String email,
    @Schema(
            description = "Password, 15 to 64 characters",
            example = "correct horse battery staple",
            minLength = 15,
            maxLength = 64)
        @NotBlank(message = "Password is required") @Size(min = 15, max = 64, message = "Password must be between 15 and 64 characters") String password) {}
