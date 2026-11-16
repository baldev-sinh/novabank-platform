package com.novabank.ledger.application.port;

import com.novabank.ledger.domain.model.RejectionReason;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Remembers refusals, so the outcome for a payment is final: a re-sent command gets the same answer
 * even if, say, the money has arrived in the meantime.
 */
public interface RejectedPostingStore {

  void save(UUID paymentId, RejectionReason reason, Instant rejectedAt);

  Optional<RejectionReason> find(UUID paymentId);
}
