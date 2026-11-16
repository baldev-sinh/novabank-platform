package com.novabank.account.infrastructure.persistence.account;

import com.novabank.account.domain.model.Account;
import com.novabank.account.domain.repository.AccountRepository;
import com.novabank.account.domain.valueobject.AccountId;
import com.novabank.account.domain.valueobject.AccountNumber;
import java.util.Currency;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class JpaAccountRepositoryAdapter implements AccountRepository {

  private final SpringDataAccountRepository repository;

  @Override
  public Account save(Account account) {
    Objects.requireNonNull(account, "Account cannot be null");
    // Flush now so constraint violations surface here, inside the use case.
    return toDomain(repository.saveAndFlush(toEntity(account)));
  }

  @Override
  public Optional<Account> findById(AccountId id) {
    return repository.findById(id.value()).map(JpaAccountRepositoryAdapter::toDomain);
  }

  @Override
  public List<Account> findByOwner(UUID ownerUserId) {
    return repository.findByOwnerUserIdOrderByOpenedAtAsc(ownerUserId).stream()
        .map(JpaAccountRepositoryAdapter::toDomain)
        .toList();
  }

  @Override
  public boolean existsByNumber(AccountNumber number) {
    return repository.existsByAccountNumber(number.value());
  }

  private static AccountEntity toEntity(Account account) {
    return AccountEntity.of(
        account.id().value(),
        account.number().value(),
        account.ownerUserId(),
        account.customerId(),
        account.type(),
        account.currency().getCurrencyCode(),
        account.status(),
        account.openedAt(),
        account.updatedAt(),
        account.version());
  }

  private static Account toDomain(AccountEntity entity) {
    return Account.restore(
        new AccountId(entity.getId()),
        new AccountNumber(entity.getAccountNumber()),
        entity.getOwnerUserId(),
        entity.getCustomerId(),
        entity.getType(),
        Currency.getInstance(entity.getCurrency()),
        entity.getStatus(),
        entity.getOpenedAt(),
        entity.getUpdatedAt(),
        entity.getVersion());
  }
}
