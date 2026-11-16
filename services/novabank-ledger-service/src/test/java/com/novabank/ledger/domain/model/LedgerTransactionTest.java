package com.novabank.ledger.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.novabank.ledger.domain.exception.PostingRejectedException;
import com.novabank.platform.money.Money;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class LedgerTransactionTest {

  private static final Clock CLOCK =
      Clock.fixed(Instant.parse("2026-11-16T10:00:00Z"), ZoneOffset.UTC);
  private static final UUID A = UUID.randomUUID();
  private static final UUID B = UUID.randomUUID();
  private static final UUID C = UUID.randomUUID();

  private static Posting debit(UUID account, String amount, String currency) {
    return new Posting(account, Direction.DEBIT, Money.of(amount, currency));
  }

  private static Posting credit(UUID account, String amount, String currency) {
    return new Posting(account, Direction.CREDIT, Money.of(amount, currency));
  }

  @Test
  @DisplayName("Balanced postings make a transaction, including one debit split over two credits")
  void balanced() {
    LedgerTransaction tx =
        LedgerTransaction.create(
            UUID.randomUUID(),
            TransactionType.TRANSFER,
            List.of(debit(A, "100.00", "INR"), credit(B, "60.00", "INR"), credit(C, "40", "INR")),
            CLOCK);

    assertThat(tx.postings()).hasSize(3);
    assertThat(tx.postedAt()).isEqualTo(Instant.parse("2026-11-16T10:00:00Z"));
  }

  @Test
  @DisplayName("Debits must equal credits in every currency")
  void rejectsUnbalanced() {
    assertRejected(List.of(debit(A, "100.00", "INR"), credit(B, "99.99", "INR")));
    // Totals match numerically but not per currency.
    assertRejected(List.of(debit(A, "10", "INR"), credit(B, "10", "EUR"), debit(C, "10", "EUR")));
  }

  @Test
  @DisplayName("At least two postings, each account at most once, positive amounts")
  void rejectsMalformed() {
    assertRejected(List.of(debit(A, "1", "INR")));
    assertRejected(List.of(debit(A, "1", "INR"), credit(A, "1", "INR")));
    assertThatThrownBy(() -> debit(A, "0", "INR"))
        .isInstanceOf(PostingRejectedException.class)
        .extracting("reason")
        .isEqualTo(RejectionReason.INVALID_POSTINGS);
    assertThatThrownBy(() -> credit(A, "-5", "INR")).isInstanceOf(PostingRejectedException.class);
  }

  private static void assertRejected(List<Posting> postings) {
    assertThatThrownBy(() ->
            LedgerTransaction.create(UUID.randomUUID(), TransactionType.TRANSFER, postings, CLOCK))
        .isInstanceOf(PostingRejectedException.class)
        .extracting("reason")
        .isEqualTo(RejectionReason.INVALID_POSTINGS);
  }
}
