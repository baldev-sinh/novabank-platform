package com.novabank.payment.application.service;

import com.novabank.payment.application.command.DepositCommand;
import com.novabank.payment.application.command.TransferCommand;
import com.novabank.payment.application.port.IdempotencyGuard;
import com.novabank.payment.application.port.PaymentStore;
import com.novabank.payment.application.port.SagaOutbox;
import com.novabank.payment.application.response.PaymentResponse;
import com.novabank.payment.application.settings.SagaSettings;
import com.novabank.payment.application.usecase.InitiatePaymentUseCase;
import com.novabank.payment.domain.model.Payment;
import java.time.Clock;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The payment row, its first command and the idempotency record commit together: a payment can't
 * exist without its first step being sent, and a retried request can't start a second saga.
 */
@Service
@RequiredArgsConstructor
public class InitiatePaymentService implements InitiatePaymentUseCase {

  private final PaymentStore payments;
  private final SagaOutbox outbox;
  private final IdempotencyGuard idempotency;
  private final SagaSettings settings;
  private final Clock clock;

  @Override
  @Transactional
  public PaymentResponse transfer(TransferCommand command) {
    return idempotency.once(
        command.requestedBy(),
        command.idempotencyKey(),
        command.fingerprint(),
        PaymentResponse.class,
        () -> {
          Payment payment =
              Payment.transfer(
                  command.requestedBy(),
                  command.fromAccountId(),
                  command.toAccountNumber(),
                  command.amount(),
                  command.reference(),
                  clock);
          payments.insert(payment, clock.instant().plus(settings.stepTimeout()));
          outbox.holdFunds(payment);
          return PaymentResponse.from(payment);
        });
  }

  @Override
  @Transactional
  public PaymentResponse deposit(DepositCommand command) {
    return idempotency.once(
        command.requestedBy(),
        command.idempotencyKey(),
        command.fingerprint(),
        PaymentResponse.class,
        () -> {
          Payment payment =
              Payment.deposit(
                  command.requestedBy(),
                  command.toAccountId(),
                  command.amount(),
                  command.reference(),
                  clock);
          payments.insert(payment, clock.instant().plus(settings.stepTimeout()));
          outbox.postLedgerTransaction(payment);
          return PaymentResponse.from(payment);
        });
  }
}
