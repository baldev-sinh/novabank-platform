package com.novabank.auth.application.verification;

import java.util.UUID;

/**
 * In-process event: send a verification email once the transaction that stored the token has
 * committed. Never published to Kafka or the outbox, because it carries the raw token.
 */
public record VerificationEmailRequested(UUID userId, String email, String token) {

  /** No email or token in logs. */
  @Override
  public String toString() {
    return "VerificationEmailRequested[userId=" + userId + "]";
  }
}
