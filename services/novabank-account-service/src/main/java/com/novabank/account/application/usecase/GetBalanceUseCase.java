package com.novabank.account.application.usecase;

import com.novabank.account.application.response.BalanceResponse;
import java.util.UUID;

public interface GetBalanceUseCase {

  /** Owner only; someone else's account is a 404. */
  BalanceResponse balance(UUID ownerUserId, UUID accountId);
}
