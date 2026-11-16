package com.novabank.account.domain.valueobject;

import java.util.Objects;
import java.util.UUID;

/** Internal account identifier (ADR-004: UUIDs internally and in events). */
public record AccountId(UUID value) {

  public AccountId {
    Objects.requireNonNull(value, "AccountId cannot be null");
  }

  public static AccountId random() {
    return new AccountId(UUID.randomUUID());
  }
}
