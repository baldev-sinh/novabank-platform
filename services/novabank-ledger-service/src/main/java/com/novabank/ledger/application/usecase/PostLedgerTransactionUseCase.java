package com.novabank.ledger.application.usecase;

import com.novabank.ledger.application.command.PostLedgerTransactionCommand;

public interface PostLedgerTransactionUseCase {

  /**
   * Posts the transaction, or records why it can't be posted, and replies with an event either way.
   * Safe to call more than once: every call for a payment gets the same outcome.
   */
  Outcome post(PostLedgerTransactionCommand command);

  enum Outcome {
    POSTED,
    REJECTED,
    /** This exact message was already handled. */
    DUPLICATE
  }
}
