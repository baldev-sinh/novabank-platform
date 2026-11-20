package com.novabank.account.application.usecase;

import java.util.UUID;

public interface ActivateAccountUseCase {

  /**
   * Handles a LedgerAccountOpened event: the PENDING account becomes ACTIVE. Safe to call more than
   * once with the same event id.
   *
   * @return {@code false} if this event was already handled or the account needed no change
   */
  boolean activate(UUID eventId, UUID accountId);
}
