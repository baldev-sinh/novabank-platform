package com.novabank.ledger.application.service;

import com.novabank.ledger.application.event.LedgerAccountOpenedEvent;
import com.novabank.ledger.application.port.LedgerAccountStore;
import com.novabank.ledger.application.port.LedgerEventOutbox;
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
  private final LedgerEventOutbox events;

  /**
   * Creates the ledger side of a customer account and announces it (6.4): the account service keeps
   * the account PENDING until LedgerAccountOpened arrives, so no money is sent to an account the
   * ledger doesn't know yet. The event commits with the account, or not at all.
   */
  @Override
  @Transactional
  public boolean open(UUID eventId, UUID accountId, String currency, Instant openedAt) {
    if (!processedEvents.markProcessed(eventId, EVENT_TYPE)) {
      return false;
    }
    accounts.createCustomerAccount(accountId, Currency.getInstance(currency), openedAt);
    events.add(LedgerAccountOpenedEvent.of(accountId, currency, openedAt));
    return true;
  }
}
