package com.novabank.account.application.port.customer;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Local read model of KYC-verified customers, filled from {@code CustomerVerified} events. Lets
 * this service decide who may open an account without calling the customer service.
 */
public interface EligibleCustomerStore {

  /** Idempotent: saving the same customer again changes nothing. */
  void save(UUID customerId, UUID userId, Instant verifiedAt);

  /** The verified customer id for this user, if they passed KYC. */
  Optional<UUID> findCustomerIdByUserId(UUID userId);
}
