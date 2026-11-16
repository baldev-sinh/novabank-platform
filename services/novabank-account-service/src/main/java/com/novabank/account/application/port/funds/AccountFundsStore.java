package com.novabank.account.application.port.funds;

import com.novabank.account.domain.model.AccountFunds;
import com.novabank.account.domain.model.AccountFunds.Beneficiary;
import com.novabank.account.domain.valueobject.AccountNumber;
import java.util.Optional;
import java.util.UUID;

public interface AccountFundsStore {

  /** Loads the account's funds and locks them until the transaction ends. */
  Optional<AccountFunds> lock(UUID accountId);

  /** Without a lock, for reads. */
  Optional<AccountFunds> find(UUID accountId);

  Optional<Beneficiary> findBeneficiary(AccountNumber number);

  /** Stores the projected ledger balance and sequence. */
  void saveProjection(AccountFunds funds);
}
