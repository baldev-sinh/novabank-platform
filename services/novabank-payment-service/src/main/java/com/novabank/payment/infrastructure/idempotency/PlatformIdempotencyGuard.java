package com.novabank.payment.infrastructure.idempotency;

import com.novabank.payment.application.port.IdempotencyGuard;
import com.novabank.platform.idempotency.IdempotentRequests;
import java.util.UUID;
import java.util.function.Supplier;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

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
