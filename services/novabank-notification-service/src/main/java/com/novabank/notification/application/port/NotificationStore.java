package com.novabank.notification.application.port;

import com.novabank.notification.domain.Notification;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface NotificationStore {

  /**
   * Stores a pending notification due now.
   *
   * @return {@code false} if one of this kind already exists for the event
   */
  boolean add(Notification notification);

  /**
   * Claims up to {@code limit} due notifications: counts the attempt and hides them until {@code
   * leaseUntil}, in its own short transaction. If this instance dies before reporting the result,
   * another one picks them up after the lease (at-least-once).
   */
  List<Notification> claimDue(Instant now, Instant leaseUntil, int limit);

  void markSent(UUID id, Instant sentAt);

  /** Back to PENDING, due again at {@code nextAttemptAt}. */
  void retryLater(UUID id, Instant nextAttemptAt, String error);

  void markFailed(UUID id, String error);
}
