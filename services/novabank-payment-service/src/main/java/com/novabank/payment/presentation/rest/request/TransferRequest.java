package com.novabank.payment.presentation.rest.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.UUID;

/** No sender identity in the body: the caller comes from the token. */
public record TransferRequest(
    @NotNull(message = "fromAccountId is required") UUID fromAccountId,
    @Schema(example = "123456789012")
        @NotBlank(message = "toAccountNumber is required") @Size(max = 20, message = "toAccountNumber is too long") String toAccountNumber,
    @Schema(description = "Decimal string", example = "125.50")
        @NotBlank(message = "amount is required") @Pattern(regexp = "\\d{1,15}(\\.\\d{1,4})?", message = "amount must be a positive decimal")
        String amount,
    @Schema(example = "INR")
        @NotBlank(message = "currency is required") @Pattern(regexp = "[A-Z]{3}", message = "currency must be an ISO 4217 code") String currency,
    @Size(max = 140, message = "reference must be at most 140 characters") String reference) {}
