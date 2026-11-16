package com.novabank.payment.application.response;

import com.novabank.payment.domain.model.Payment;
import java.time.Instant;
import java.util.UUID;

public record PaymentResponse(
    UUID paymentId,
    String type,
    String status,
    UUID fromAccountId,
    String toAccountNumber,
    UUID toAccountId,
    String amount,
    String currency,
    String reference,
    String failureReason,
    Instant createdAt,
    Instant updatedAt) {

  public static PaymentResponse from(Payment payment) {
    return new PaymentResponse(
        payment.id(),
        payment.type().name(),
        payment.status().name(),
        payment.fromAccountId(),
        payment.toAccountNumber(),
        payment.toAccountId(),
        payment.amount().amountString(),
        payment.amount().currencyCode(),
        payment.reference(),
        payment.failureReason(),
        payment.createdAt(),
        payment.updatedAt());
  }
}
