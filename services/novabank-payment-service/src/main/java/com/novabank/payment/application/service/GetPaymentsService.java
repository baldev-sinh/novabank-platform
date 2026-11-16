package com.novabank.payment.application.service;

import com.novabank.payment.application.exception.PaymentNotFoundException;
import com.novabank.payment.application.port.PaymentStore;
import com.novabank.payment.application.response.PaymentResponse;
import com.novabank.payment.application.usecase.GetPaymentsUseCase;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class GetPaymentsService implements GetPaymentsUseCase {

  static final int MAX_RESULTS = 50;

  private final PaymentStore payments;

  @Override
  public PaymentResponse getOwn(UUID userId, UUID paymentId) {
    return payments
        .find(paymentId)
        .filter(payment -> payment.isOwnedBy(userId))
        .map(PaymentResponse::from)
        .orElseThrow(PaymentNotFoundException::new);
  }

  @Override
  public List<PaymentResponse> listOwn(UUID userId) {
    return payments.findByRequester(userId, MAX_RESULTS).stream()
        .map(PaymentResponse::from)
        .toList();
  }

  @Override
  public PaymentResponse get(UUID paymentId) {
    return payments
        .find(paymentId)
        .map(PaymentResponse::from)
        .orElseThrow(PaymentNotFoundException::new);
  }
}
