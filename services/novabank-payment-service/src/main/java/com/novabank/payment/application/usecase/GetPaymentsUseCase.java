package com.novabank.payment.application.usecase;

import com.novabank.payment.application.response.PaymentPage;
import com.novabank.payment.application.response.PaymentResponse;
import java.util.UUID;

public interface GetPaymentsUseCase {

  /** Owner only; someone else's payment is a 404. */
  PaymentResponse getOwn(UUID userId, UUID paymentId);

  /** Newest first. */
  /**
   * One page of the customer's own payments, newest first.
   *
   * @param before the previous page's cursor; null for the first page
   * @throws com.novabank.payment.domain.exception.DomainException if the cursor is malformed
   */
  PaymentPage listOwn(UUID userId, String before, int limit);

  /** For operations staff: any payment. */
  PaymentResponse get(UUID paymentId);
}
