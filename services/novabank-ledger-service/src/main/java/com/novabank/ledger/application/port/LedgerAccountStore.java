package com.novabank.ledger.application.port;

import com.novabank.ledger.domain.model.LedgerAccount;
import java.time.Instant;
import java.util.Collection;
import java.util.Currency;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface LedgerAccountStore {

  /** Creates the ledger side of a customer account; idempotent. */
  void createCustomerAccount(UUID accountId, Currency currency, Instant createdAt);

  Optional<UUID> findSystemAccountId(String code, Currency currency);

  /**
   * Locks the accounts for this transaction, always in id order so two postings can never deadlock.
   * Missing ids are simply absent from the result.
   */
  List<LedgerAccount> lockForUpdate(Collection<UUID> ids);

  void updateBalances(Collection<LedgerAccount> accounts);

  Optional<LedgerAccount> findById(UUID id);
}
