package com.novabank.payment.application.port;

import java.util.Optional;
import java.util.UUID;

/**
 * Asks the account service about an account (7.3): the first synchronous call between NovaBank
 * services, made with a service token.
 */
public interface AccountDirectory {

  /**
   * @return empty if the account doesn't exist
   * @throws com.novabank.payment.application.exception.AccountServiceUnavailableException if the
   *     account service can't answer (timeout, error, circuit open)
   */
  Optional<AccountSnapshot> find(UUID accountId);

  /**
   * @param status the account service's status name, e.g. {@code ACTIVE} or {@code PENDING}
   */
  record AccountSnapshot(UUID accountId, String status, String currency) {

    public boolean isActive() {
      return "ACTIVE".equals(status);
    }
  }
}
