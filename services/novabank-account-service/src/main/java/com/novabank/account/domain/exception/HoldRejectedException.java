package com.novabank.account.domain.exception;

import com.novabank.account.domain.model.HoldRejectionReason;

/** Funds can't be held. A business outcome, reported back to the payment saga. */
public class HoldRejectedException extends DomainException {

  private final HoldRejectionReason reason;

  public HoldRejectedException(HoldRejectionReason reason) {
    super("Funds can't be held: " + reason);
    this.reason = reason;
  }

  public HoldRejectionReason reason() {
    return reason;
  }
}
