package com.novabank.ledger.application.port;

import com.novabank.ledger.application.event.LedgerAccountOpenedEvent;
import com.novabank.ledger.application.event.LedgerBalanceSnapshotEvent;
import com.novabank.ledger.application.event.LedgerPostingFailedEvent;
import com.novabank.ledger.application.event.LedgerTransactionPostedEvent;

/** Must be called inside the transaction that makes the change: both commit, or neither. */
public interface LedgerEventOutbox {

  void add(LedgerTransactionPostedEvent event);

  void add(LedgerPostingFailedEvent event);

  void add(LedgerAccountOpenedEvent event);

  void add(LedgerBalanceSnapshotEvent event);
}
