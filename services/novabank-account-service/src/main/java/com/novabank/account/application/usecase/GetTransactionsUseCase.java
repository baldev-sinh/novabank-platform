package com.novabank.account.application.usecase;

import com.novabank.account.application.response.TransactionPageResponse;
import java.util.UUID;

public interface GetTransactionsUseCase {

  /**
   * One page of an account's transaction history, newest first.
   *
   * @param beforeSequence the {@code nextBefore} of the previous page; null for the first page
   * @throws com.novabank.account.application.exception.AccountNotFoundException if unknown or owned
   *     by someone else
   */
  TransactionPageResponse transactions(
      UUID ownerUserId, UUID accountId, Long beforeSequence, int limit);
}
