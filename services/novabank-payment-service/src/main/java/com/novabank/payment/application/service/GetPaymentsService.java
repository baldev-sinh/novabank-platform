package com.novabank.payment.application.service;

import com.novabank.payment.application.exception.PaymentNotFoundException;
import com.novabank.payment.application.port.PaymentStore;
import com.novabank.payment.application.query.PaymentCursor;
import com.novabank.payment.application.response.PaymentPage;
import com.novabank.payment.application.response.PaymentResponse;
import com.novabank.payment.application.usecase.GetPaymentsUseCase;
import com.novabank.payment.domain.model.Payment;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class GetPaymentsService implements GetPaymentsUseCase {

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
  public PaymentPage listOwn(UUID userId, String before, int limit) {
    PaymentCursor cursor = before == null ? null : PaymentCursor.decode(before);
    // One extra row says whether an older page exists, without a count query.
    List<Payment> rows = payments.findByRequester(userId, cursor, limit + 1);
    boolean more = rows.size() > limit;
    List<Payment> page = more ? rows.subList(0, limit) : rows;
    Payment last = page.isEmpty() ? null : page.getLast();
    return new PaymentPage(
        page.stream().map(PaymentResponse::from).toList(),
        more ? new PaymentCursor(last.createdAt(), last.id()).encode() : null);
  }

  @Override
  public PaymentResponse get(UUID paymentId) {
    return payments
        .find(paymentId)
        .map(PaymentResponse::from)
        .orElseThrow(PaymentNotFoundException::new);
  }
}
