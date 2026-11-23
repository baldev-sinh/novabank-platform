package com.novabank.notification.domain;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * An email to send. Exactly one of {@code recipientUserId} and {@code recipientAccountId} is set;
 * an account means "whoever owns it", looked up when sending.
 *
 * @param details what the email shows (amount, currency, account ids, failure reason)
 * @param attempts send attempts so far, counting the current one once claimed
 */
public record Notification(
    UUID id,
    UUID eventId,
    NotificationKind kind,
    UUID recipientUserId,
    UUID recipientAccountId,
    Map<String, String> details,
    int attempts,
    Instant createdAt) {

  public Notification {
    Objects.requireNonNull(id, "id cannot be null");
    Objects.requireNonNull(eventId, "eventId cannot be null");
    Objects.requireNonNull(kind, "kind cannot be null");
    if ((recipientUserId == null) == (recipientAccountId == null)) {
      throw new IllegalArgumentException("Exactly one recipient (user or account) is required");
    }
    details = Map.copyOf(details);
  }

  public static Notification toUser(
      UUID eventId, NotificationKind kind, UUID userId, Map<String, String> details, Instant now) {
    return new Notification(UUID.randomUUID(), eventId, kind, userId, null, details, 0, now);
  }

  public static Notification toAccountOwner(
      UUID eventId,
      NotificationKind kind,
      UUID accountId,
      Map<String, String> details,
      Instant now) {
    return new Notification(UUID.randomUUID(), eventId, kind, null, accountId, details, 0, now);
  }
}
