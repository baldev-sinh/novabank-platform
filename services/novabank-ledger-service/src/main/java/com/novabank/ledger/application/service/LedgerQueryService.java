package com.novabank.ledger.application.service;

import com.novabank.ledger.application.exception.LedgerAccountNotFoundException;
import com.novabank.ledger.application.port.LedgerAccountStore;
import com.novabank.ledger.application.port.LedgerTransactionStore;
import com.novabank.ledger.application.port.ReconciliationQuery;
import com.novabank.ledger.application.response.LedgerAccountResponse;
import com.novabank.ledger.application.response.LedgerEntryResponse;
import com.novabank.ledger.application.response.ReconciliationReport;
import com.novabank.ledger.application.usecase.LedgerQueryUseCase;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class LedgerQueryService implements LedgerQueryUseCase {

  static final int MAX_ENTRIES = 200;

  private final LedgerAccountStore accounts;
  private final LedgerTransactionStore transactions;
  private final ReconciliationQuery reconciliation;

  @Override
  public LedgerAccountResponse account(UUID accountId) {
    return accounts
        .findById(accountId)
        .map(LedgerAccountResponse::from)
        .orElseThrow(LedgerAccountNotFoundException::new);
  }

  @Override
  public List<LedgerEntryResponse> entries(UUID accountId, int limit) {
    account(accountId);
    return transactions.entriesOf(accountId, Math.clamp(limit, 1, MAX_ENTRIES)).stream()
        .map(LedgerEntryResponse::from)
        .toList();
  }

  @Override
  public ReconciliationReport reconcile() {
    List<String> mismatched = reconciliation.accountsWithBalanceMismatch();
    List<String> unbalanced = reconciliation.unbalancedCurrencies();
    return new ReconciliationReport(
        mismatched.isEmpty() && unbalanced.isEmpty(), mismatched, unbalanced);
  }
}
