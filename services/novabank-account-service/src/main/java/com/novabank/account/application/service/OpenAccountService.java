package com.novabank.account.application.service;

import com.novabank.account.application.command.OpenAccountCommand;
import com.novabank.account.application.event.AccountOpenedEvent;
import com.novabank.account.application.exception.CustomerNotVerifiedException;
import com.novabank.account.application.exception.UnsupportedCurrencyException;
import com.novabank.account.application.port.customer.EligibleCustomerStore;
import com.novabank.account.application.port.idempotency.IdempotencyGuard;
import com.novabank.account.application.port.messaging.AccountEventOutbox;
import com.novabank.account.application.response.AccountResponse;
import com.novabank.account.application.settings.AccountSettings;
import com.novabank.account.application.usecase.OpenAccountUseCase;
import com.novabank.account.domain.model.Account;
import com.novabank.account.domain.repository.AccountRepository;
import com.novabank.account.domain.valueobject.AccountNumber;
import java.security.SecureRandom;
import java.time.Clock;
import java.util.Currency;
import java.util.Objects;
import java.util.UUID;
import java.util.random.RandomGenerator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class OpenAccountService implements OpenAccountUseCase {

  /** 9 billion possible numbers, so a collision is rare; a few tries make failure negligible. */
  static final int NUMBER_ATTEMPTS = 5;

  private static final RandomGenerator RANDOM = new SecureRandom();

  private final AccountRepository accounts;
  private final EligibleCustomerStore eligibleCustomers;
  private final AccountEventOutbox events;
  private final IdempotencyGuard idempotency;
  private final AccountSettings settings;
  private final Clock clock;

  /** Account, AccountOpened event and idempotency record commit together. */
  @Override
  @Transactional
  public AccountResponse open(OpenAccountCommand command) {
    Objects.requireNonNull(command, "command cannot be null");
    return idempotency.once(
        command.ownerUserId(),
        command.idempotencyKey(),
        command.fingerprint(),
        AccountResponse.class,
        () -> openNew(command));
  }

  private AccountResponse openNew(OpenAccountCommand command) {
    Currency currency = supportedCurrency(command.currency());
    UUID customerId =
        eligibleCustomers
            .findCustomerIdByUserId(command.ownerUserId())
            .orElseThrow(CustomerNotVerifiedException::new);

    Account saved =
        accounts.save(
            Account.open(
                unusedNumber(),
                command.ownerUserId(),
                customerId,
                command.type(),
                currency,
                clock));
    events.add(AccountOpenedEvent.of(saved));
    return AccountResponse.from(saved);
  }

  /**
   * Checked before inserting, not by catching the unique violation: in PostgreSQL a failed
   * statement aborts the whole transaction, so the insert couldn't be retried in it. The unique
   * constraint is still the final guard against a concurrent collision (the request then fails and
   * can be retried with the same Idempotency-Key, because the claim rolls back too).
   */
  private AccountNumber unusedNumber() {
    for (int attempt = 0; attempt < NUMBER_ATTEMPTS; attempt++) {
      AccountNumber candidate = AccountNumber.generate(RANDOM);
      if (!accounts.existsByNumber(candidate)) {
        return candidate;
      }
    }
    throw new IllegalStateException("No unused account number after " + NUMBER_ATTEMPTS + " tries");
  }

  private Currency supportedCurrency(String code) {
    try {
      Currency currency = Currency.getInstance(code);
      if (settings.supportedCurrencies().contains(currency)) {
        return currency;
      }
    } catch (IllegalArgumentException ex) {
      // Not an ISO 4217 code: same answer as an unsupported one.
    }
    throw new UnsupportedCurrencyException(code);
  }
}
