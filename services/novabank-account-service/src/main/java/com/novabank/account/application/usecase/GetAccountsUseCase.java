package com.novabank.account.application.usecase;

import com.novabank.account.application.response.AccountResponse;
import java.util.List;
import java.util.UUID;

public interface GetAccountsUseCase {

  List<AccountResponse> listOwn(UUID ownerUserId);

  /**
   * @throws com.novabank.account.application.exception.AccountNotFoundException if unknown or owned
   *     by someone else
   */
  AccountResponse getOwn(UUID ownerUserId, UUID accountId);
}
