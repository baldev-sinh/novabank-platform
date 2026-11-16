package com.novabank.ledger.domain.model;

public enum TransactionType {
  /** Money enters the bank: debit SETTLEMENT, credit the customer. */
  DEPOSIT,
  /** Between two customer accounts. */
  TRANSFER
}
