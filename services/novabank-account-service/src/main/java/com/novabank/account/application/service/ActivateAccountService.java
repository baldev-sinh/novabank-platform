package com.novabank.account.application.service;

import com.novabank.account.application.port.messaging.ProcessedEventStore;
import com.novabank.account.application.usecase.ActivateAccountUseCase;
import com.novabank.account.domain.model.Account;
import com.novabank.account.domain.repository.AccountRepository;
import com.novabank.account.domain.valueobject.AccountId;
import java.time.Clock;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** PENDING → ACTIVE when the ledger confirms it holds the account (6.4). */
@Service
@RequiredArgsConstructor
public class ActivateAccountService implements ActivateAccountUseCase {

  static final String EVENT_TYPE = "LedgerAccountOpened.v1";

  private static final Logger LOGGER = LoggerFactory.getLogger(ActivateAccountService.class);

  private final ProcessedEventStore processedEvents;
  private final AccountRepository accounts;
  private final Clock clock;

  /** "Mark processed" and the status change commit together: applied once despite redelivery. */
  @Override
  @Transactional
  public boolean activate(UUID eventId, UUID accountId) {
    if (!processedEvents.markProcessed(eventId, EVENT_TYPE)) {
      return false;
    }
    Optional<Account> found = accounts.findById(new AccountId(accountId));
    if (found.isEmpty()) {
      // The ledger only opens accounts this service announced, so this points at a bug upstream.
      LOGGER.warn("LedgerAccountOpened for unknown account {}", accountId);
      return false;
    }
    Account account = found.get();
    // Accounts opened before 6.4 are already ACTIVE; a FROZEN or CLOSED one stays as it is.
    if (!account.isPending()) {
      return false;
    }
    account.activate(clock);
    accounts.save(account);
    return true;
  }
}
