package com.novabank.account.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.novabank.account.application.command.OpenAccountCommand;
import com.novabank.account.application.event.AccountOpenedEvent;
import com.novabank.account.application.exception.CustomerNotVerifiedException;
import com.novabank.account.application.exception.UnsupportedCurrencyException;
import com.novabank.account.application.port.customer.EligibleCustomerStore;
import com.novabank.account.application.port.idempotency.IdempotencyGuard;
import com.novabank.account.application.port.messaging.AccountEventOutbox;
import com.novabank.account.application.response.AccountResponse;
import com.novabank.account.application.settings.AccountSettings;
import com.novabank.account.domain.model.Account;
import com.novabank.account.domain.model.AccountStatus;
import com.novabank.account.domain.model.AccountType;
import com.novabank.account.domain.repository.AccountRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Currency;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class OpenAccountServiceTest {

  private static final Instant NOW = Instant.parse("2026-11-16T10:00:00Z");
  private static final UUID USER = UUID.randomUUID();
  private static final UUID CUSTOMER = UUID.randomUUID();

  @Mock private AccountRepository accounts;
  @Mock private EligibleCustomerStore eligibleCustomers;
  @Mock private AccountEventOutbox events;

  /** Runs the action directly; the real store is covered by AccountOpeningIT. */
  private final IdempotencyGuard passThrough =
      new IdempotencyGuard() {
        @Override
        public <T> T once(UUID owner, String key, String fp, Class<T> type, Supplier<T> action) {
          return action.get();
        }
      };

  private OpenAccountService service;

  @BeforeEach
  void setUp() {
    service =
        new OpenAccountService(
            accounts,
            eligibleCustomers,
            events,
            passThrough,
            new AccountSettings(Set.of(Currency.getInstance("INR"), Currency.getInstance("EUR"))),
            Clock.fixed(NOW, ZoneOffset.UTC));
  }

  @Test
  @DisplayName("A verified customer gets an ACTIVE account and AccountOpened is recorded")
  void opensAccount() {
    when(eligibleCustomers.findCustomerIdByUserId(USER)).thenReturn(Optional.of(CUSTOMER));
    when(accounts.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

    AccountResponse response = service.open(command("INR"));

    assertThat(response.status()).isEqualTo(AccountStatus.ACTIVE);
    assertThat(response.currency()).isEqualTo("INR");
    ArgumentCaptor<Account> saved = ArgumentCaptor.forClass(Account.class);
    verify(accounts).save(saved.capture());
    assertThat(saved.getValue().customerId()).isEqualTo(CUSTOMER);
    assertThat(saved.getValue().ownerUserId()).isEqualTo(USER);
    ArgumentCaptor<AccountOpenedEvent> event = ArgumentCaptor.forClass(AccountOpenedEvent.class);
    verify(events).add(event.capture());
    assertThat(event.getValue().accountId()).isEqualTo(response.accountId());
    assertThat(event.getValue().accountNumber()).isEqualTo(response.accountNumber());
  }

  @Test
  @DisplayName("A customer without KYC can't open an account")
  void requiresVerifiedCustomer() {
    when(eligibleCustomers.findCustomerIdByUserId(USER)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.open(command("INR")))
        .isInstanceOf(CustomerNotVerifiedException.class);
    verify(accounts, never()).save(any());
  }

  @Test
  @DisplayName("Unsupported or unknown currencies are rejected")
  void rejectsCurrency() {
    assertThatThrownBy(() -> service.open(command("JPY")))
        .isInstanceOf(UnsupportedCurrencyException.class);
    assertThatThrownBy(() -> service.open(command("ZZZ")))
        .isInstanceOf(UnsupportedCurrencyException.class);
  }

  @Test
  @DisplayName("A number that is already taken is replaced by a fresh one")
  void retriesTakenNumber() {
    when(eligibleCustomers.findCustomerIdByUserId(USER)).thenReturn(Optional.of(CUSTOMER));
    when(accounts.existsByNumber(any())).thenReturn(true, false);
    when(accounts.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

    assertThat(service.open(command("EUR")).accountNumber()).hasSize(12);
  }

  private static OpenAccountCommand command(String currency) {
    return new OpenAccountCommand(USER, "key-12345678", AccountType.CURRENT, currency);
  }
}
