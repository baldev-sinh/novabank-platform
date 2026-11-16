package com.novabank.payment.application.command;

import java.util.UUID;

/** A reply from the account or ledger service, already parsed. */
public sealed interface SagaReply {

  UUID eventId();

  UUID paymentId();

  String type();

  record FundsHeld(UUID eventId, UUID paymentId, UUID holdId, UUID beneficiaryAccountId)
      implements SagaReply {
    public String type() {
      return "FundsHeld.v1";
    }
  }

  record FundsHoldFailed(UUID eventId, UUID paymentId, String reason) implements SagaReply {
    public String type() {
      return "FundsHoldFailed.v1";
    }
  }

  record HoldReleased(UUID eventId, UUID paymentId, String outcome) implements SagaReply {
    public String type() {
      return "HoldReleased.v1";
    }
  }

  record BalancesUpdated(UUID eventId, UUID paymentId, UUID transactionId) implements SagaReply {
    public String type() {
      return "BalancesUpdated.v1";
    }
  }

  record LedgerPosted(UUID eventId, UUID paymentId, UUID transactionId) implements SagaReply {
    public String type() {
      return "LedgerTransactionPosted.v1";
    }
  }

  record LedgerFailed(UUID eventId, UUID paymentId, String reason) implements SagaReply {
    public String type() {
      return "LedgerPostingFailed.v1";
    }
  }
}
