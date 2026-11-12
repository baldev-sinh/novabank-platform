package com.novabank.customer.presentation.rest.customer.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

/**
 * Personal details for a new customer profile. There's no user id here on purpose: it comes from
 * the caller's access token, so nobody can create a profile for another user.
 */
public record RegisterCustomerRequest(
    @Schema(example = "Baldev")
        @NotBlank(message = "First name is required") @Size(max = 100, message = "First name must be at most 100 characters") String firstName,
    @Schema(example = "Parmar")
        @NotBlank(message = "Last name is required") @Size(max = 100, message = "Last name must be at most 100 characters") String lastName,
    @Schema(example = "2001-01-15")
        @NotNull(message = "Date of birth is required") @Past(message = "Date of birth must be in the past") LocalDate dateOfBirth,
    @Schema(description = "International format", example = "+919876543210")
        @NotBlank(message = "Phone is required") @Size(max = 32, message = "Phone must be at most 32 characters") String phone) {}
