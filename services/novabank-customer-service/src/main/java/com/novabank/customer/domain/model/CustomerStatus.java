package com.novabank.customer.domain.model;

/**
 * Lifecycle of a customer profile.
 *
 * <pre>
 *   PENDING_KYC --activate--> ACTIVE --deactivate--> INACTIVE --activate--> ACTIVE
 *        any (except BLOCKED) --block--> BLOCKED --unblock--> ACTIVE
 * </pre>
 */
public enum CustomerStatus {

  /** Registered, identity checks (KYC) not completed yet. Cannot transact. */
  PENDING_KYC,

  /** KYC completed; the customer can use banking products. */
  ACTIVE,

  /** Closed or dormant at the customer's request; can be reactivated. */
  INACTIVE,

  /** Blocked by the bank (fraud, compliance). Only an explicit unblock lifts it. */
  BLOCKED
}
