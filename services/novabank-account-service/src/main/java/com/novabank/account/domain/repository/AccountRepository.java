package com.novabank.account.domain.repository;

import com.novabank.account.domain.model.Account;
import com.novabank.account.domain.valueobject.AccountId;
import com.novabank.account.domain.valueobject.AccountNumber;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AccountRepository {

  Account save(Account account);

  Optional<Account> findById(AccountId id);

  /** The user's accounts, oldest first. */
  List<Account> findByOwner(UUID ownerUserId);

  boolean existsByNumber(AccountNumber number);
}
