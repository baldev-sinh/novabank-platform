package com.novabank.payment.presentation.rest;

import com.novabank.payment.application.command.DepositCommand;
import com.novabank.payment.application.response.PaymentResponse;
import com.novabank.payment.application.usecase.GetPaymentsUseCase;
import com.novabank.payment.application.usecase.InitiatePaymentUseCase;
import com.novabank.payment.presentation.rest.request.DepositRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import java.net.URI;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Operations staff (Decision N): money entering the bank, posted double-entry from SETTLEMENT. */
@Validated
@RestController
@RequestMapping(value = "/api/v1/admin", produces = MediaType.APPLICATION_JSON_VALUE)
@RequiredArgsConstructor
public class AdminPaymentController {

  private final InitiatePaymentUseCase initiate;
  private final GetPaymentsUseCase payments;
  private final AmountLimit amountLimit;

  @PostMapping(value = "/deposits", consumes = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<PaymentResponse> deposit(
      @AuthenticationPrincipal Jwt jwt,
      @RequestHeader("Idempotency-Key")
          @Pattern(
              regexp = "[A-Za-z0-9_-]{8,100}",
              message = "Idempotency-Key must be 8-100 characters of A-Z, a-z, 0-9, _ or -")
          String idempotencyKey,
      @Valid @RequestBody DepositRequest request) {
    PaymentResponse payment =
        initiate.deposit(
            new DepositCommand(
                UUID.fromString(jwt.getSubject()),
                idempotencyKey,
                request.toAccountId(),
                amountLimit.parse(request.amount(), request.currency()),
                request.reference()));
    return ResponseEntity.accepted()
        .location(URI.create("/api/v1/admin/payments/" + payment.paymentId()))
        .body(payment);
  }

  @GetMapping("/payments/{paymentId}")
  public PaymentResponse get(@PathVariable UUID paymentId) {
    return payments.get(paymentId);
  }
}
