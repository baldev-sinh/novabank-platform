package com.novabank.customer.application.port.messaging;

import java.time.Instant;
import java.util.UUID;

/** Local copy of user accounts announced by the auth service. */
public interface KnownUserStore {

  void save(UUID userId, String email, Instant registeredAt);
}
