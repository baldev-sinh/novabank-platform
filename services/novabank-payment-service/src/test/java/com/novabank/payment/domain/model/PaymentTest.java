package com.novabank.payment.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.novabank.payment.domain.exception.DomainException;
import com.novabank.platform.money.Money;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PaymentTest {

  private static final Clock CLOCK =
      Clock.fixed(Instant.parse("2026-11-16T10:00:00Z"), ZoneOffset.UTC);
  private static final UUID TX = UUID.randomUUID();

  private static Payment transfer() {
    return Payment.transfer(
        UUID.randomUUID(),
        UUID.randomUUID(),
        "123456789012",
        Money.of("30", "INR"),
        " rent ",
        CLOCK);
  }

  private static Payment deposit() {
    return Payment.deposit(
        UUID.randomUUID(), UUID.randomUUID(), Money.of("100", "INR"), null, CLOCK);
  }

  @Test
  @DisplayName("Transfer happy path: INITIATED → FUNDS_HELD → LEDGER_POSTED → COMPLETED")
  void transferHappyPath() {
    Payment payment = transfer();
    assertThat(payment.reference()).isEqualTo("rent");

    UUID beneficiary = UUID.randomUUID();
    assertThat(payment.onFundsHeld(UUID.randomUUID(), beneficiary, CLOCK)).isTrue();
    assertThat(payment.toAccountId()).isEqualTo(beneficiary);
    assertThat(payment.onLedgerPosted(TX, CLOCK)).isTrue();
    assertThat(payment.onBalancesUpdated(TX, CLOCK)).isTrue();

    assertThat(payment.status()).isEqualTo(PaymentStatus.COMPLETED);
    assertThat(payment.ledgerTransactionId()).isEqualTo(TX);
  }

  @Test
  @DisplayName("Ledger refusal after a hold compensates first; a hold refusal fails directly")
  void failures() {
    Payment ledgerRefused = transfer();
    ledgerRefused.onFundsHeld(UUID.randomUUID(), UUID.randomUUID(), CLOCK);
    assertThat(ledgerRefused.onLedgerFailed("INSUFFICIENT_FUNDS", CLOCK)).isTrue();
    assertThat(ledgerRefused.status()).isEqualTo(PaymentStatus.COMPENSATING);
    assertThat(ledgerRefused.onHoldReleased(CLOCK)).isTrue();
    assertThat(ledgerRefused.status()).isEqualTo(PaymentStatus.FAILED);
    assertThat(ledgerRefused.failureReason()).isEqualTo("INSUFFICIENT_FUNDS");

    Payment holdRefused = transfer();
    assertThat(holdRefused.onFundsHoldFailed("NOT_ACCOUNT_OWNER", CLOCK)).isTrue();
    assertThat(holdRefused.status()).isEqualTo(PaymentStatus.FAILED);
  }

  @Test
  @DisplayName(
      "Replies from two topics may come out of order; late or repeated ones change nothing")
  void outOfOrderAndLateReplies() {
    Payment payment = transfer();
    payment.onFundsHeld(UUID.randomUUID(), UUID.randomUUID(), CLOCK);
    // BalancesUpdated (account topic) overtakes LedgerTransactionPosted (ledger topic).
    assertThat(payment.onBalancesUpdated(TX, CLOCK)).isTrue();
    assertThat(payment.status()).isEqualTo(PaymentStatus.COMPLETED);
    assertThat(payment.ledgerTransactionId()).isEqualTo(TX);

    assertThat(payment.onLedgerPosted(TX, CLOCK)).isFalse();
    assertThat(payment.onFundsHeld(UUID.randomUUID(), UUID.randomUUID(), CLOCK)).isFalse();
    assertThat(payment.onLedgerFailed("X", CLOCK)).isFalse();
    assertThat(payment.onHoldReleased(CLOCK)).isFalse();
    assertThat(payment.status()).isEqualTo(PaymentStatus.COMPLETED);
  }

  @Test
  @DisplayName("A deposit has no hold: INITIATED → LEDGER_POSTED → COMPLETED, or FAILED")
  void deposits() {
    Payment ok = deposit();
    assertThat(ok.onFundsHeld(UUID.randomUUID(), UUID.randomUUID(), CLOCK)).isFalse();
    assertThat(ok.onLedgerPosted(TX, CLOCK)).isTrue();
    assertThat(ok.onBalancesUpdated(TX, CLOCK)).isTrue();
    assertThat(ok.status()).isEqualTo(PaymentStatus.COMPLETED);

    Payment refused = deposit();
    assertThat(refused.onLedgerFailed("UNKNOWN_ACCOUNT", CLOCK)).isTrue();
    assertThat(refused.status()).isEqualTo(PaymentStatus.FAILED);
  }

  @Test
  @DisplayName("Amount must be positive; reference at most 140 characters")
  void validation() {
    assertThatThrownBy(() ->
            Payment.deposit(
                UUID.randomUUID(), UUID.randomUUID(), Money.of("0", "INR"), null, CLOCK))
        .isInstanceOf(DomainException.class);
    assertThatThrownBy(() ->
            Payment.deposit(
                UUID.randomUUID(), UUID.randomUUID(), Money.of("1", "INR"), "x".repeat(141), CLOCK))
        .isInstanceOf(DomainException.class);
  }
}
