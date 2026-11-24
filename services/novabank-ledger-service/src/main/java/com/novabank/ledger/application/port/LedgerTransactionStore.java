package com.novabank.ledger.application.port;

import com.novabank.ledger.domain.model.LedgerTransaction;
import com.novabank.ledger.domain.model.PostedEntry;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface LedgerTransactionStore {

  void save(LedgerTransaction transaction, List<PostedEntry> entries);

  Optional<Stored> findByPaymentId(UUID paymentId);

  /** Newest first. */
  List<StoredEntry> entriesOf(UUID accountId, int limit);

  record Stored(
      UUID transactionId,
      String type,
      String reference,
      Instant postedAt,
      List<PostedEntry> entries) {}

  record StoredEntry(UUID transactionId, UUID paymentId, Instant postedAt, PostedEntry entry) {}
}
