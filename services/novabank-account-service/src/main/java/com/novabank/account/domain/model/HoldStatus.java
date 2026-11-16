package com.novabank.account.domain.model;

/** ACTIVE → RELEASED (payment failed) or ACTIVE → CAPTURED (the ledger moved the money). */
public enum HoldStatus {
  ACTIVE,
  RELEASED,
  CAPTURED
}
