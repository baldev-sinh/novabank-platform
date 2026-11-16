package com.novabank.account.application.port.idempotency;

import java.util.UUID;
import java.util.function.Supplier;

/** Runs a create operation at most once per (caller, Idempotency-Key). */
public interface IdempotencyGuard {

  /**
   * @return the action's result, or the stored result of the earlier request with this key
   */
  <T> T once(UUID ownerId, String key, String fingerprint, Class<T> type, Supplier<T> action);
}
