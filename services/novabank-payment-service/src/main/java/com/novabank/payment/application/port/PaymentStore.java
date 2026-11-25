package com.novabank.payment.application.port;

import com.novabank.payment.application.query.PaymentCursor;
import com.novabank.payment.domain.model.Payment;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PaymentStore {

  /** Stores a new payment; its first step is due at {@code nextAttemptAt}. */
  void insert(Payment payment, Instant nextAttemptAt);

  /** Saves state; a new step restarts the attempt counter. */
  void update(Payment payment, Instant nextAttemptAt, boolean newStep);

  /** Loads and locks the payment, so replies for one payment are handled one at a time. */
  Optional<Payment> lock(UUID paymentId);

  Optional<Payment> find(UUID paymentId);

  /**
   * A customer's payments, newest first.
   *
   * @param before only payments older than this; null for the newest
   */
  List<Payment> findByRequester(UUID userId, PaymentCursor before, int limit);

  /**
   * Non-terminal payments whose step is overdue, locked with {@code SKIP LOCKED} so several
   * instances never pick the same one.
   */
  List<Overdue> lockOverdue(Instant now, int limit);

  /** Records another try of the current step and when the next one is due. */
  void recordAttempt(UUID paymentId, int attempts, Instant nextAttemptAt, boolean needsAttention);

  record Overdue(Payment payment, int attempts) {}
}
