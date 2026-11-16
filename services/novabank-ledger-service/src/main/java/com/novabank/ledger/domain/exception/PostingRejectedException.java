package com.novabank.ledger.domain.exception;

import com.novabank.ledger.domain.model.RejectionReason;

/** The transaction can't be posted. A business outcome, reported back to the payment saga. */
public class PostingRejectedException extends DomainException {

  private final RejectionReason reason;

  public PostingRejectedException(RejectionReason reason, String message) {
    super(message);
    this.reason = reason;
  }

  public RejectionReason reason() {
    return reason;
  }
}
