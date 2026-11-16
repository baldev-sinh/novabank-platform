package com.novabank.ledger.presentation.rest;

import com.novabank.ledger.application.response.LedgerAccountResponse;
import com.novabank.ledger.application.response.LedgerEntryResponse;
import com.novabank.ledger.application.response.ReconciliationReport;
import com.novabank.ledger.application.usecase.LedgerQueryUseCase;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Read-only, for bank staff (OPERATIONS, ADMIN). */
@RestController
@RequestMapping(value = "/api/v1/ledger", produces = MediaType.APPLICATION_JSON_VALUE)
@RequiredArgsConstructor
public class LedgerController {

  private final LedgerQueryUseCase ledger;

  @GetMapping("/accounts/{accountId}")
  public LedgerAccountResponse account(@PathVariable UUID accountId) {
    return ledger.account(accountId);
  }

  /** Newest first, at most 200. */
  @GetMapping("/accounts/{accountId}/entries")
  public List<LedgerEntryResponse> entries(
      @PathVariable UUID accountId, @RequestParam(defaultValue = "50") int limit) {
    return ledger.entries(accountId, limit);
  }

  /** Every cached balance equals its entries, and debits equal credits per currency. */
  @GetMapping("/reconciliation")
  public ReconciliationReport reconciliation() {
    return ledger.reconcile();
  }
}
