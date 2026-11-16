package com.novabank.customer.application.port.compliance;

import java.time.Instant;
import java.util.UUID;

/** Append-only audit trail of KYC decisions: who decided, and when. */
public interface KycDecisionLog {

  void recordApproval(UUID customerId, UUID decidedBy, Instant decidedAt);
}
