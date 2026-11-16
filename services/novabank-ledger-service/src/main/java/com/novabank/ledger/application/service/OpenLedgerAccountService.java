package com.novabank.ledger.application.service;

import com.novabank.ledger.application.port.LedgerAccountStore;
import com.novabank.ledger.application.port.ProcessedEventStore;
import com.novabank.ledger.application.usecase.OpenLedgerAccountUseCase;
import java.time.Instant;
import java.util.Currency;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class OpenLedgerAccountService implements OpenLedgerAccountUseCase {

  static final String EVENT_TYPE = "AccountOpened.v1";

  private final ProcessedEventStore processedEvents;
  private final LedgerAccountStore accounts;

  @Override
  @Transactional
  public boolean open(UUID eventId, UUID accountId, String currency, Instant openedAt) {
    if (!processedEvents.markProcessed(eventId, EVENT_TYPE)) {
      return false;
    }
    accounts.createCustomerAccount(accountId, Currency.getInstance(currency), openedAt);
    return true;
  }
}
