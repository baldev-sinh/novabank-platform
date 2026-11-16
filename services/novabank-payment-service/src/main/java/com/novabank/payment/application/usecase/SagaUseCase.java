package com.novabank.payment.application.usecase;

import com.novabank.payment.application.command.SagaReply;

public interface SagaUseCase {

  /** Moves the saga on one reply. Safe for duplicates and late replies (they change nothing). */
  Outcome onReply(SagaReply reply);

  /** Re-sends the command of every overdue step; returns how many were re-sent. */
  int resendOverdueSteps();

  enum Outcome {
    APPLIED,
    /** Doesn't fit the payment's current state (late, repeated or out of order). */
    IGNORED,
    /** Not a payment of this service. */
    UNKNOWN_PAYMENT,
    /** This exact message was already handled. */
    DUPLICATE
  }
}
