package com.novabank.account.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.novabank.account.domain.exception.HoldRejectedException;
import com.novabank.account.domain.model.AccountFunds.Beneficiary;
import com.novabank.platform.money.Money;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Currency;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class AccountFundsTest {

  private static final Clock CLOCK =
      Clock.fixed(Instant.parse("2026-11-16T10:00:00Z"), ZoneOffset.UTC);
  private static final Currency INR = Currency.getInstance("INR");
  private static final UUID OWNER = UUID.randomUUID();
  private static final Beneficiary BENEFICIARY =
      new Beneficiary(UUID.randomUUID(), AccountStatus.ACTIVE, INR);

  private static AccountFunds funds(AccountStatus status, String balance, String held) {
    return new AccountFunds(
        UUID.randomUUID(), OWNER, status, INR, Money.of(balance, "INR"), Money.of(held, "INR"), 5);
  }

  private static Hold hold(AccountFunds funds, Beneficiary to, String amount) {
    return funds.placeHold(UUID.randomUUID(), OWNER, to, Money.of(amount, "INR"), CLOCK);
  }

  @Test
  @DisplayName("Available = ledger balance - held; a hold reduces it and can use it all")
  void holdsReduceAvailable() {
    AccountFunds funds = funds(AccountStatus.ACTIVE, "100", "20");
    assertThat(funds.available()).isEqualTo(Money.of("80", "INR"));

    Hold hold = hold(funds, BENEFICIARY, "80");

    assertThat(hold.isActive()).isTrue();
    assertThat(funds.available().isZero()).isTrue();
    assertRejected(() -> hold(funds, BENEFICIARY, "0.01"), HoldRejectionReason.INSUFFICIENT_FUNDS);
  }

  @Test
  @DisplayName("Every rule has its own reason")
  void rejectionReasons() {
    AccountFunds active = funds(AccountStatus.ACTIVE, "100", "0");

    assertRejected(
        () ->
            active.placeHold(
                UUID.randomUUID(), UUID.randomUUID(), BENEFICIARY, Money.of("1", "INR"), CLOCK),
        HoldRejectionReason.NOT_ACCOUNT_OWNER);
    assertRejected(
        () -> hold(funds(AccountStatus.FROZEN, "100", "0"), BENEFICIARY, "1"),
        HoldRejectionReason.ACCOUNT_NOT_ACTIVE);
    assertRejected(
        () -> hold(active, new Beneficiary(active.accountId(), AccountStatus.ACTIVE, INR), "1"),
        HoldRejectionReason.SAME_ACCOUNT);
    assertRejected(
        () -> hold(active, new Beneficiary(UUID.randomUUID(), AccountStatus.CLOSED, INR), "1"),
        HoldRejectionReason.BENEFICIARY_NOT_ACTIVE);
    assertRejected(
        () ->
            hold(
                active,
                new Beneficiary(
                    UUID.randomUUID(), AccountStatus.ACTIVE, Currency.getInstance("EUR")),
                "1"),
        HoldRejectionReason.CURRENCY_MISMATCH);
  }

  @Test
  @DisplayName("The projection only moves forward: older or repeated ledger entries are ignored")
  void projectionMovesForward() {
    AccountFunds funds = funds(AccountStatus.ACTIVE, "100", "0");

    assertThat(funds.applyLedgerEntry(7, Money.of("70", "INR"))).isTrue();
    assertThat(funds.applyLedgerEntry(7, Money.of("999", "INR"))).isFalse();
    assertThat(funds.applyLedgerEntry(6, Money.of("85", "INR"))).isFalse();

    assertThat(funds.ledgerBalance()).isEqualTo(Money.of("70", "INR"));
    assertThat(funds.lastLedgerSequence()).isEqualTo(7);
  }

  @Test
  @DisplayName("A hold is released or captured once, never both")
  void holdLifecycle() {
    Hold released = hold(funds(AccountStatus.ACTIVE, "10", "0"), BENEFICIARY, "5");
    released.release();
    assertThat(released.status()).isEqualTo(HoldStatus.RELEASED);
    assertThatThrownBy(released::capture).hasMessageContaining("not ACTIVE");

    Hold captured = hold(funds(AccountStatus.ACTIVE, "10", "0"), BENEFICIARY, "5");
    captured.capture();
    assertThatThrownBy(captured::release).hasMessageContaining("not ACTIVE");
  }

  private static void assertRejected(Runnable action, HoldRejectionReason reason) {
    assertThatThrownBy(action::run)
        .isInstanceOf(HoldRejectedException.class)
        .extracting("reason")
        .isEqualTo(reason);
  }
}
