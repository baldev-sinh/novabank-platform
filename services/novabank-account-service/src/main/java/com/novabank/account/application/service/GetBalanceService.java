package com.novabank.account.application.service;

import com.novabank.account.application.exception.AccountNotFoundException;
import com.novabank.account.application.port.funds.AccountFundsStore;
import com.novabank.account.application.response.BalanceResponse;
import com.novabank.account.application.usecase.GetAccountsUseCase;
import com.novabank.account.application.usecase.GetBalanceUseCase;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class GetBalanceService implements GetBalanceUseCase {

  private final GetAccountsUseCase accounts;
  private final AccountFundsStore funds;

  @Override
  public BalanceResponse balance(UUID ownerUserId, UUID accountId) {
    // Ownership check (404 for someone else's account) lives in one place.
    accounts.getOwn(ownerUserId, accountId);
    return funds
        .find(accountId)
        .map(BalanceResponse::from)
        .orElseThrow(AccountNotFoundException::new);
  }
}
