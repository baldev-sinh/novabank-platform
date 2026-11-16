package com.novabank.customer.presentation.rest.compliance;

import com.novabank.customer.application.response.CustomerDetailsResponse;
import com.novabank.customer.application.usecase.ApproveKycUseCase;
import com.novabank.customer.application.usecase.ListPendingKycUseCase;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Staff-only KYC review. Its own path ({@code /api/v1/compliance}, COMPLIANCE_OFFICER role), so
 * staff and customer self-service rules can never be mixed up.
 */
@RestController
@RequestMapping(value = "/api/v1/compliance/customers", produces = MediaType.APPLICATION_JSON_VALUE)
@RequiredArgsConstructor
public class ComplianceController {

  private final ListPendingKycUseCase listPendingKyc;
  private final ApproveKycUseCase approveKyc;

  /** Review queue: customers waiting for KYC, oldest first. */
  @GetMapping("/pending-kyc")
  public List<CustomerDetailsResponse> pendingKyc() {
    return listPendingKyc.listPending();
  }

  /**
   * Approves the customer's KYC. Idempotent: a retry returns the same result without a second
   * CustomerVerified event. The approver is recorded from the token, never from the request.
   */
  @PostMapping("/{customerId}/kyc-approval")
  public CustomerDetailsResponse approve(
      @PathVariable UUID customerId, @AuthenticationPrincipal Jwt jwt) {
    return approveKyc.approve(customerId, UUID.fromString(jwt.getSubject()));
  }
}
