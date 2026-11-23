package com.novabank.account.application.service;

import com.novabank.account.application.exception.AccountNotFoundException;
import com.novabank.account.application.response.AccountResponse;
import com.novabank.account.application.usecase.GetAccountsUseCase;
import com.novabank.account.domain.repository.AccountRepository;
import com.novabank.account.domain.valueobject.AccountId;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class GetAccountsService implements GetAccountsUseCase {

  private final AccountRepository accounts;

  @Override
  public List<AccountResponse> listOwn(UUID ownerUserId) {
    return accounts.findByOwner(ownerUserId).stream().map(AccountResponse::from).toList();
  }

  @Override
  public AccountResponse getOwn(UUID ownerUserId, UUID accountId) {
    return accounts
        .findById(new AccountId(accountId))
        // Someone else's account looks exactly like a missing one (no ID enumeration).
        .filter(account -> account.isOwnedBy(ownerUserId))
        .map(AccountResponse::from)
        .orElseThrow(AccountNotFoundException::new);
  }

  @Override
  public AccountResponse get(UUID accountId) {
    return accounts
        .findById(new AccountId(accountId))
        .map(AccountResponse::from)
        .orElseThrow(AccountNotFoundException::new);
  }
}
