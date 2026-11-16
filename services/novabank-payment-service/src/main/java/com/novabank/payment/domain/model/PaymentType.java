package com.novabank.payment.domain.model;

public enum PaymentType {
  /** Customer to customer: hold, post, complete. */
  TRANSFER,
  /** Money enters from outside (operations staff): no hold, post from SETTLEMENT. */
  DEPOSIT
}
