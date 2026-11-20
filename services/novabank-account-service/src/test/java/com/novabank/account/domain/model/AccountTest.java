package com.novabank.account.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.novabank.account.domain.exception.DomainException;
import com.novabank.account.domain.valueobject.AccountNumber;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Currency;
import java.util.Random;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class AccountTest {

  private static final Instant NOW = Instant.parse("2026-11-16T10:00:00Z");
  private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);
  private static final Clock LATER = Clock.fixed(NOW.plusSeconds(60), ZoneOffset.UTC);
  private static final UUID OWNER = UUID.randomUUID();

  private static Account open() {
    return Account.open(
        AccountNumber.generate(new Random()),
        OWNER,
        UUID.randomUUID(),
        AccountType.CURRENT,
        Currency.getInstance("INR"),
        CLOCK);
  }

  @Test
  @DisplayName("A new account is PENDING until the ledger has it, owned by the caller, not stored")
  void opensPending() {
    Account account = open();

    assertThat(account.status()).isEqualTo(AccountStatus.PENDING);
    assertThat(account.isPending()).isTrue();
    assertThat(account.isOwnedBy(OWNER)).isTrue();
    assertThat(account.isOwnedBy(UUID.randomUUID())).isFalse();
    assertThat(account.openedAt()).isEqualTo(NOW);
    assertThat(account.version()).isNull();
  }

  @Test
  @DisplayName("Only a PENDING account can be activated, and only once")
  void activation() {
    Account account = open();
    assertThatThrownBy(() -> account.freeze(CLOCK))
        .isInstanceOf(DomainException.class)
        .hasMessage("Cannot freeze an account in status PENDING.");

    account.activate(LATER);

    assertThat(account.status()).isEqualTo(AccountStatus.ACTIVE);
    assertThat(account.updatedAt()).isEqualTo(NOW.plusSeconds(60));
    assertThatThrownBy(() -> account.activate(CLOCK)).isInstanceOf(DomainException.class);
  }

  @Test
  @DisplayName("Freeze, unfreeze and close follow the lifecycle; CLOSED is final")
  void lifecycle() {
    Account account = open();
    account.activate(CLOCK);

    account.freeze(LATER);
    assertThat(account.status()).isEqualTo(AccountStatus.FROZEN);
    assertThat(account.updatedAt()).isEqualTo(NOW.plusSeconds(60));
    assertThatThrownBy(() -> account.freeze(CLOCK)).isInstanceOf(DomainException.class);

    account.unfreeze(CLOCK);
    assertThat(account.status()).isEqualTo(AccountStatus.ACTIVE);
    assertThatThrownBy(() -> account.unfreeze(CLOCK)).isInstanceOf(DomainException.class);

    account.close(CLOCK);
    assertThat(account.status()).isEqualTo(AccountStatus.CLOSED);
    assertThatThrownBy(() -> account.freeze(CLOCK))
        .isInstanceOf(DomainException.class)
        .hasMessage("Cannot freeze an account in status CLOSED.");
    assertThatThrownBy(() -> account.close(CLOCK)).isInstanceOf(DomainException.class);
  }

  @Test
  @DisplayName("toString has no owner or account number")
  void toStringHasNoIdentifiers() {
    Account account = open();

    assertThat(account.toString())
        .doesNotContain(OWNER.toString())
        .doesNotContain(account.number().value());
  }
}
