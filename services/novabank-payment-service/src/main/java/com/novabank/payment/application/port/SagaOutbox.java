package com.novabank.payment.application.port;

import com.novabank.payment.domain.model.Payment;

/**
 * Commands to the other services and payment events, written in the same transaction as the state
 * change (outbox). Each send has a new message id; receivers deduplicate per payment.
 */
public interface SagaOutbox {

  void holdFunds(Payment payment);

  void releaseHold(Payment payment);

  void postLedgerTransaction(Payment payment);

  /** PaymentCompleted or PaymentFailed, for notification and audit. */
  void paymentFinished(Payment payment);
}
