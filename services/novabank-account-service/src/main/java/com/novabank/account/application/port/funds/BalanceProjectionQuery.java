package com.novabank.account.application.port.funds;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.Map;
import java.util.UUID;

/** Reads the balance projection for the drift check (6.2). */
public interface BalanceProjectionQuery {

  /**
   * The projected balance and last applied ledger sequence of each given account that exists here.
   * An account with no projection row yet is reported as balance 0 at sequence 0, the same as the
   * ledger starts it.
   */
  Map<UUID, ProjectedBalance> find(Collection<UUID> accountIds);

  record ProjectedBalance(BigDecimal balance, long sequence) {}
}
