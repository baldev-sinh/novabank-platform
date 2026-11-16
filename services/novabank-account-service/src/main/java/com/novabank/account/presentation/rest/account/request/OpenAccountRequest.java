package com.novabank.account.presentation.rest.account.request;

import com.novabank.account.domain.model.AccountType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

/** No owner or customer id: both come from the caller's token and the KYC read model. */
public record OpenAccountRequest(
    @Schema(example = "CURRENT") @NotNull(message = "Type is required") AccountType type,
    @Schema(description = "ISO 4217 code", example = "INR")
        @NotNull(message = "Currency is required") @Pattern(regexp = "[A-Z]{3}", message = "Currency must be a 3-letter ISO 4217 code") String currency) {}
