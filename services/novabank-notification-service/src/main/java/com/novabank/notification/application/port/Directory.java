package com.novabank.notification.application.port;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/** This service's own copy of users and accounts (Decision AI: no calls at send time). */
public interface Directory {

  void saveUser(UUID userId, String email, Instant registeredAt);

  void saveAccount(UUID accountId, String accountNumber, UUID ownerUserId, String currency);

  Optional<String> emailOf(UUID userId);

  Optional<KnownAccount> account(UUID accountId);

  record KnownAccount(UUID accountId, String accountNumber, UUID ownerUserId, String currency) {}
}
