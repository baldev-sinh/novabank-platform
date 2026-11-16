package com.novabank.payment.application.usecase;

import com.novabank.payment.application.response.PaymentResponse;
import java.util.List;
import java.util.UUID;

public interface GetPaymentsUseCase {

  /** Owner only; someone else's payment is a 404. */
  PaymentResponse getOwn(UUID userId, UUID paymentId);

  /** Newest first. */
  List<PaymentResponse> listOwn(UUID userId);

  /** For operations staff: any payment. */
  PaymentResponse get(UUID paymentId);
}
