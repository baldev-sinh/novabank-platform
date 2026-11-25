package com.novabank.account.application.service;

import com.novabank.account.application.port.funds.AccountTransactionStore;
import com.novabank.account.application.response.TransactionPageResponse;
import com.novabank.account.application.response.TransactionResponse;
import com.novabank.account.application.usecase.GetAccountsUseCase;
import com.novabank.account.application.usecase.GetTransactionsUseCase;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class GetTransactionsService implements GetTransactionsUseCase {

  private final GetAccountsUseCase accounts;
  private final AccountTransactionStore transactions;

  @Override
  public TransactionPageResponse transactions(
      UUID ownerUserId, UUID accountId, Long beforeSequence, String direction, int limit) {
    // Ownership check (404 for someone else's account) lives in one place.
    accounts.getOwn(ownerUserId, accountId);
    // One extra row says whether an older page exists, without a count query.
    List<AccountTransactionStore.View> rows =
        transactions.page(accountId, beforeSequence, direction, limit + 1);
    boolean more = rows.size() > limit;
    List<AccountTransactionStore.View> page = more ? rows.subList(0, limit) : rows;
    return new TransactionPageResponse(
        page.stream().map(TransactionResponse::from).toList(),
        more ? page.getLast().sequence() : null);
  }
}
