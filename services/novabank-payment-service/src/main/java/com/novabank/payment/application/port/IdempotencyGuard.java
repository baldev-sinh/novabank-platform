package com.novabank.payment.application.port;

import java.util.UUID;
import java.util.function.Supplier;

/** Runs a create operation at most once per (caller, Idempotency-Key). */
public interface IdempotencyGuard {

  <T> T once(UUID ownerId, String key, String fingerprint, Class<T> type, Supplier<T> action);
}
