package com.novabank.account.application.usecase;

import com.novabank.account.application.command.HoldFundsCommand;
import com.novabank.account.application.command.LedgerPosting;
import java.util.UUID;

/** The account side of the payment saga. Every method is safe to call more than once. */
public interface FundsUseCase {

  /** Reserves funds, or records why not, and replies FundsHeld / FundsHoldFailed. */
  Outcome hold(HoldFundsCommand command);

  /** Releases the payment's hold (compensation) and replies HoldReleased. */
  Outcome release(UUID commandId, UUID paymentId);

  /** Projects a ledger posting: balances forward, hold captured, BalancesUpdated. */
  Outcome applyLedgerPosting(LedgerPosting posting);

  enum Outcome {
    APPLIED,
    REJECTED,
    /** Nothing for this service to do (e.g. no account of ours in the posting). */
    IGNORED,
    /** This exact message was already handled. */
    DUPLICATE
  }
}
