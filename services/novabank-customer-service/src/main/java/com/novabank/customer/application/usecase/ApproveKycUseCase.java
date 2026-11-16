package com.novabank.customer.application.usecase;

import com.novabank.customer.application.response.CustomerDetailsResponse;
import java.util.UUID;

public interface ApproveKycUseCase {

  /**
   * Approves the customer's KYC, which lets them open accounts. Safe to retry: approving an
   * already-approved customer returns it unchanged, without a second event.
   *
   * @param approvedBy the compliance officer's user id
   * @throws com.novabank.customer.application.exception.CustomerNotFoundException if unknown
   * @throws com.novabank.customer.application.exception.CustomerStateConflictException if the
   *     customer is neither pending KYC nor already approved
   */
  CustomerDetailsResponse approve(UUID customerId, UUID approvedBy);
}
