package com.novabank.account.domain.model;

/** Why funds couldn't be held. Part of the FundsHoldFailed contract: never rename. */
public enum HoldRejectionReason {
  ACCOUNT_NOT_FOUND,
  NOT_ACCOUNT_OWNER,
  ACCOUNT_NOT_ACTIVE,
  BENEFICIARY_NOT_FOUND,
  BENEFICIARY_NOT_ACTIVE,
  SAME_ACCOUNT,
  CURRENCY_MISMATCH,
  INSUFFICIENT_FUNDS
}
