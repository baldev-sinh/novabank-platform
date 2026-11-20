package com.novabank.account.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.novabank.account.application.port.messaging.ProcessedEventStore;
import com.novabank.account.domain.model.Account;
import com.novabank.account.domain.model.AccountStatus;
import com.novabank.account.domain.model.AccountType;
import com.novabank.account.domain.repository.AccountRepository;
import com.novabank.account.domain.valueobject.AccountId;
import com.novabank.account.domain.valueobject.AccountNumber;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Currency;
import java.util.Optional;
import java.util.Random;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ActivateAccountServiceTest {

  private static final Clock CLOCK =
      Clock.fixed(Instant.parse("2026-11-20T10:00:00Z"), ZoneOffset.UTC);

  @Mock private ProcessedEventStore processedEvents;
  @Mock private AccountRepository accounts;

  private ActivateAccountService service;

  @BeforeEach
  void setUp() {
    service = new ActivateAccountService(processedEvents, accounts, CLOCK);
  }

  @Test
  @DisplayName("LedgerAccountOpened makes the PENDING account ACTIVE")
  void activatesPendingAccount() {
    UUID eventId = UUID.randomUUID();
    Account account = pendingAccount();
    when(processedEvents.markProcessed(eventId, "LedgerAccountOpened.v1")).thenReturn(true);
    when(accounts.findById(account.id())).thenReturn(Optional.of(account));

    assertThat(service.activate(eventId, account.id().value())).isTrue();

    assertThat(account.status()).isEqualTo(AccountStatus.ACTIVE);
    verify(accounts).save(account);
  }

  @Test
  @DisplayName("A redelivered event changes nothing")
  void duplicateIsIgnored() {
    UUID eventId = UUID.randomUUID();
    when(processedEvents.markProcessed(eventId, "LedgerAccountOpened.v1")).thenReturn(false);

    assertThat(service.activate(eventId, UUID.randomUUID())).isFalse();

    verify(accounts, never()).findById(any());
  }

  @Test
  @DisplayName("An account that is already past PENDING (e.g. FROZEN) is left as it is")
  void nonPendingAccountIsLeftAlone() {
    Account account = pendingAccount();
    account.activate(CLOCK);
    account.freeze(CLOCK);
    when(processedEvents.markProcessed(any(), any())).thenReturn(true);
    when(accounts.findById(account.id())).thenReturn(Optional.of(account));

    assertThat(service.activate(UUID.randomUUID(), account.id().value())).isFalse();

    assertThat(account.status()).isEqualTo(AccountStatus.FROZEN);
    verify(accounts, never()).save(any());
  }

  @Test
  @DisplayName("An unknown account is logged and skipped, not retried forever")
  void unknownAccountIsSkipped() {
    UUID unknown = UUID.randomUUID();
    when(processedEvents.markProcessed(any(), any())).thenReturn(true);
    when(accounts.findById(new AccountId(unknown))).thenReturn(Optional.empty());

    assertThat(service.activate(UUID.randomUUID(), unknown)).isFalse();

    verify(accounts, never()).save(any());
  }

  private static Account pendingAccount() {
    return Account.open(
        AccountNumber.generate(new Random()),
        UUID.randomUUID(),
        UUID.randomUUID(),
        AccountType.CURRENT,
        Currency.getInstance("INR"),
        CLOCK);
  }
}
