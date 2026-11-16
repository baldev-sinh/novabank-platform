package com.novabank.account.application.port.funds;

import com.novabank.account.domain.model.HoldRejectionReason;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/** Final "no" per payment, so a re-sent HoldFunds gets the same answer. */
public interface HoldRejectionStore {

  void save(UUID paymentId, HoldRejectionReason reason, Instant rejectedAt);

  Optional<HoldRejectionReason> find(UUID paymentId);
}
