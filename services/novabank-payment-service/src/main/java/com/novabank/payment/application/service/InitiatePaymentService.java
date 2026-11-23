package com.novabank.payment.application.service;

import com.novabank.payment.application.command.DepositCommand;
import com.novabank.payment.application.command.TransferCommand;
import com.novabank.payment.application.exception.AccountNotActiveException;
import com.novabank.payment.application.exception.DepositAccountNotFoundException;
import com.novabank.payment.application.port.AccountDirectory;
import com.novabank.payment.application.port.AccountDirectory.AccountSnapshot;
import com.novabank.payment.application.port.IdempotencyGuard;
import com.novabank.payment.application.port.PaymentStore;
import com.novabank.payment.application.port.SagaOutbox;
import com.novabank.payment.application.response.PaymentResponse;
import com.novabank.payment.application.settings.SagaSettings;
import com.novabank.payment.application.usecase.InitiatePaymentUseCase;
import com.novabank.payment.domain.model.Payment;
import java.time.Clock;
import java.util.UUID;
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
  private final AccountDirectory accountDirectory;

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

  /**
   * Checks the account first (7.3): a deposit to an account that isn't ACTIVE is refused before a
   * payment exists, instead of failing later in the ledger. The check is part of the first
   * execution only, so a retried request still replays its stored result. It runs inside the
   * transaction; the client's timeouts (1 s connect, 2 s read) and its circuit breaker bound how
   * long that holds.
   */
  @Override
  @Transactional
  public PaymentResponse deposit(DepositCommand command) {
    return idempotency.once(
        command.requestedBy(),
        command.idempotencyKey(),
        command.fingerprint(),
        PaymentResponse.class,
        () -> {
          requireActive(command.toAccountId());
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

  private void requireActive(UUID accountId) {
    AccountSnapshot account =
        accountDirectory.find(accountId).orElseThrow(DepositAccountNotFoundException::new);
    if (!account.isActive()) {
      throw new AccountNotActiveException(account.status());
    }
  }
}
