package com.novabank.payment.domain.model;

/**
 * Saga states (docs/payment-lifecycle.md). TRANSFER: INITIATED → FUNDS_HELD → LEDGER_POSTED →
 * COMPLETED; a ledger refusal goes FUNDS_HELD → COMPENSATING (release the hold) → FAILED; a hold
 * refusal goes INITIATED → FAILED. DEPOSIT: INITIATED → LEDGER_POSTED → COMPLETED, or FAILED.
 */
public enum PaymentStatus {
  INITIATED,
  FUNDS_HELD,
  LEDGER_POSTED,
  COMPENSATING,
  COMPLETED,
  FAILED;

  public boolean isTerminal() {
    return this == COMPLETED || this == FAILED;
  }
}
