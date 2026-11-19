package com.novabank.auth.presentation.rest.admin.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Sent in a POST body, so the email never appears in URLs or access logs. */
public record FindUserRequest(
    @Schema(description = "Email address of the user", example = "staff@example.com")
        @NotBlank(message = "Email is required") @Email(message = "Invalid email address") @Size(max = 255, message = "Email must be at most 255 characters") String email) {}
