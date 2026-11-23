package com.novabank.account.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.novabank.account.application.event.AccountActivatedEvent;
import com.novabank.account.application.event.AccountEvent;
import com.novabank.account.application.port.messaging.AccountEventOutbox;
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
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ActivateAccountServiceTest {

  private static final Clock CLOCK =
      Clock.fixed(Instant.parse("2026-11-20T10:00:00Z"), ZoneOffset.UTC);

  @Mock private ProcessedEventStore processedEvents;
  @Mock private AccountRepository accounts;
  @Mock private AccountEventOutbox outbox;

  private ActivateAccountService service;

  @BeforeEach
  void setUp() {
    service = new ActivateAccountService(processedEvents, accounts, outbox, CLOCK);
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
    ArgumentCaptor<AccountEvent> event = ArgumentCaptor.forClass(AccountEvent.class);
    verify(outbox).add(event.capture());
    AccountActivatedEvent activated = (AccountActivatedEvent) event.getValue();
    assertThat(activated.eventType()).isEqualTo("AccountActivated.v1");
    assertThat(activated.accountId()).isEqualTo(account.id().value());
    assertThat(activated.ownerUserId()).isEqualTo(account.ownerUserId());
    assertThat(activated.occurredAt()).isEqualTo(CLOCK.instant());
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
    verify(outbox, never()).add(any());

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
