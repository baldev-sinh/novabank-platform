package com.novabank.payment.presentation.rest;

import com.novabank.payment.application.command.TransferCommand;
import com.novabank.payment.application.response.PaymentPage;
import com.novabank.payment.application.response.PaymentResponse;
import com.novabank.payment.application.usecase.GetPaymentsUseCase;
import com.novabank.payment.application.usecase.InitiatePaymentUseCase;
import com.novabank.payment.presentation.rest.request.TransferRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

@Validated
@RestController
@RequestMapping(value = "/api/v1/payments", produces = MediaType.APPLICATION_JSON_VALUE)
@RequiredArgsConstructor
public class PaymentController {

  private final InitiatePaymentUseCase initiate;
  private final GetPaymentsUseCase payments;
  private final AmountLimit amountLimit;

  /**
   * Starts a transfer and returns 202 at once: the saga finishes asynchronously, so poll {@code GET
   * /api/v1/payments/{id}}. {@code Idempotency-Key} is required (ADR-003): a retry returns the same
   * payment and never moves money twice.
   */
  @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<PaymentResponse> transfer(
      @AuthenticationPrincipal Jwt jwt,
      @RequestHeader("Idempotency-Key")
          @Pattern(
              regexp = "[A-Za-z0-9_-]{8,100}",
              message = "Idempotency-Key must be 8-100 characters of A-Z, a-z, 0-9, _ or -")
          String idempotencyKey,
      @Valid @RequestBody TransferRequest request) {
    PaymentResponse payment =
        initiate.transfer(
            new TransferCommand(
                userId(jwt),
                idempotencyKey,
                request.fromAccountId(),
                request.toAccountNumber(),
                amountLimit.parse(request.amount(), request.currency()),
                request.reference()));
    return ResponseEntity.accepted()
        .location(URI.create("/api/v1/payments/" + payment.paymentId()))
        .body(payment);
  }

  @GetMapping("/{paymentId}")
  public PaymentResponse get(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID paymentId) {
    return payments.getOwn(userId(jwt), paymentId);
  }

  /**
   * The caller's payments, newest first, as a plain array (as before paging, so older clients keep
   * working). If there are older ones, a {@code Link: <…>; rel="next"} header (RFC 8288) points to
   * the next page; its {@code before} cursor is opaque. {@code limit} 1-100, default 50.
   */
  @GetMapping
  public ResponseEntity<List<PaymentResponse>> list(
      @AuthenticationPrincipal Jwt jwt,
      @RequestParam(defaultValue = "50")
          @Min(value = 1, message = "limit must be between 1 and 100") @Max(value = 100, message = "limit must be between 1 and 100") int limit,
      @RequestParam(required = false) @Size(max = 200, message = "before is too long") String before) {
    PaymentPage page = payments.listOwn(userId(jwt), before, limit);
    ResponseEntity.BodyBuilder response = ResponseEntity.ok();
    if (page.nextCursor() != null) {
      // Relative (RFC 8288 allows a URI reference): right behind any proxy, whatever its host.
      String next =
          UriComponentsBuilder.fromPath("/api/v1/payments")
              .queryParam("limit", limit)
              .queryParam("before", page.nextCursor())
              .build()
              .toUriString();
      response.header(HttpHeaders.LINK, "<" + next + ">; rel=\"next\"");
    }
    return response.body(page.payments());
  }

  private static UUID userId(Jwt jwt) {
    return UUID.fromString(jwt.getSubject());
  }
}
