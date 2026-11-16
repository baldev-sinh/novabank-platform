package com.novabank.payment.application.usecase;

import com.novabank.payment.application.command.DepositCommand;
import com.novabank.payment.application.command.TransferCommand;
import com.novabank.payment.application.response.PaymentResponse;

public interface InitiatePaymentUseCase {

  /** Starts a transfer saga. The same Idempotency-Key returns the same payment. */
  PaymentResponse transfer(TransferCommand command);

  /**
   * Starts a deposit saga (operations staff). The same Idempotency-Key returns the same payment.
   */
  PaymentResponse deposit(DepositCommand command);
}
