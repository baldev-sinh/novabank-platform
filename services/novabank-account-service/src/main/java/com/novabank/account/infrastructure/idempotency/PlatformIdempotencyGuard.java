package com.novabank.account.infrastructure.idempotency;

import com.novabank.account.application.port.idempotency.IdempotencyGuard;
import com.novabank.platform.idempotency.IdempotentRequests;
import java.util.UUID;
import java.util.function.Supplier;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Adapter from the application's port to the platform's Idempotency-Key store. */
@Component
@RequiredArgsConstructor
public class PlatformIdempotencyGuard implements IdempotencyGuard {

  private final IdempotentRequests idempotentRequests;

  @Override
  public <T> T once(
      UUID ownerId, String key, String fingerprint, Class<T> type, Supplier<T> action) {
    return idempotentRequests.execute(ownerId, key, fingerprint, type, action).value();
  }
}
