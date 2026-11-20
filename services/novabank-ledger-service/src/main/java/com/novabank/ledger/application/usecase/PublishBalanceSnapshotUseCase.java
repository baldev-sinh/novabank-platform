package com.novabank.ledger.application.usecase;

public interface PublishBalanceSnapshotUseCase {

  /**
   * Publishes every customer balance, in parts, through the outbox.
   *
   * @return the number of accounts published, or -1 if another instance is taking the snapshot
   */
  int publish();
}
