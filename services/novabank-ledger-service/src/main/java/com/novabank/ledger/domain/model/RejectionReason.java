package com.novabank.ledger.domain.model;

/** Why a posting was refused. Part of the LedgerPostingFailed contract: never rename. */
public enum RejectionReason {
  UNKNOWN_ACCOUNT,
  CURRENCY_MISMATCH,
  INSUFFICIENT_FUNDS,
  INVALID_POSTINGS
}
