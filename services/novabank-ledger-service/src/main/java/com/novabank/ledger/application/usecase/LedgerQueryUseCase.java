package com.novabank.ledger.application.usecase;

import com.novabank.ledger.application.response.LedgerAccountResponse;
import com.novabank.ledger.application.response.LedgerEntryResponse;
import com.novabank.ledger.application.response.ReconciliationReport;
import java.util.List;
import java.util.UUID;

public interface LedgerQueryUseCase {

  LedgerAccountResponse account(UUID accountId);

  List<LedgerEntryResponse> entries(UUID accountId, int limit);

  ReconciliationReport reconcile();
}
