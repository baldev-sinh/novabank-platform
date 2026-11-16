package com.novabank.payment.presentation.rest.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public record DepositRequest(
    @NotNull(message = "toAccountId is required") UUID toAccountId,
    @NotBlank(message = "amount is required") @Pattern(regexp = "\\d{1,15}(\\.\\d{1,4})?", message = "amount must be a positive decimal")
        String amount,
    @NotBlank(message = "currency is required") @Pattern(regexp = "[A-Z]{3}", message = "currency must be an ISO 4217 code") String currency,
    @Size(max = 140, message = "reference must be at most 140 characters") String reference) {}
