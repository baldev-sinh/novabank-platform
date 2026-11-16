package com.novabank.ledger.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.novabank.ledger.domain.exception.PostingRejectedException;
import com.novabank.platform.money.Money;
import java.util.Currency;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class LedgerAccountTest {

  private static final Currency INR = Currency.getInstance("INR");

  private static LedgerAccount account(
      AccountKind kind, AccountClass accountClass, String balance) {
    return new LedgerAccount(
        UUID.randomUUID(), kind, accountClass, INR, Money.of(balance, "INR"), 0);
  }

  private static Posting posting(LedgerAccount account, Direction direction, String amount) {
    return new Posting(account.id(), direction, Money.of(amount, "INR"));
  }

  @Test
  @DisplayName("Customer (liability) accounts grow with credits and shrink with debits")
  void liability() {
    LedgerAccount customer = account(AccountKind.CUSTOMER, AccountClass.LIABILITY, "0");

    PostedEntry first = customer.apply(posting(customer, Direction.CREDIT, "100"));
    PostedEntry second = customer.apply(posting(customer, Direction.DEBIT, "30"));

    assertThat(first.balanceAfter()).isEqualTo(Money.of("100.00", "INR"));
    assertThat(second.balanceAfter()).isEqualTo(Money.of("70.00", "INR"));
    assertThat(first.sequence()).isEqualTo(1);
    assertThat(second.sequence()).isEqualTo(2);
    assertThat(customer.lastSequence()).isEqualTo(2);
  }

  @Test
  @DisplayName("A customer account can't go below zero; the balance is unchanged after a refusal")
  void noOverdraft() {
    LedgerAccount customer = account(AccountKind.CUSTOMER, AccountClass.LIABILITY, "50");

    assertThatThrownBy(() -> customer.apply(posting(customer, Direction.DEBIT, "50.01")))
        .isInstanceOf(PostingRejectedException.class)
        .extracting("reason")
        .isEqualTo(RejectionReason.INSUFFICIENT_FUNDS);
    assertThat(customer.balance()).isEqualTo(Money.of("50", "INR"));
    assertThat(customer.lastSequence()).isZero();
    // Exactly to zero is fine.
    assertThat(customer.apply(posting(customer, Direction.DEBIT, "50")).balanceAfter().isZero())
        .isTrue();
  }

  @Test
  @DisplayName("The settlement (asset) account grows with debits and may go negative")
  void assetSystemAccount() {
    LedgerAccount settlement = account(AccountKind.SYSTEM, AccountClass.ASSET, "0");

    assertThat(settlement.apply(posting(settlement, Direction.DEBIT, "10")).balanceAfter())
        .isEqualTo(Money.of("10", "INR"));
    assertThat(settlement.apply(posting(settlement, Direction.CREDIT, "25")).balanceAfter())
        .isEqualTo(Money.of("-15", "INR"));
  }

  @Test
  @DisplayName("A posting in another currency is refused")
  void currencyMismatch() {
    LedgerAccount customer = account(AccountKind.CUSTOMER, AccountClass.LIABILITY, "0");

    assertThatThrownBy(() ->
            customer.apply(new Posting(customer.id(), Direction.CREDIT, Money.of("1", "EUR"))))
        .isInstanceOf(PostingRejectedException.class)
        .extracting("reason")
        .isEqualTo(RejectionReason.CURRENCY_MISMATCH);
  }
}
