package com.novabank.account.infrastructure.persistence.account;

import com.novabank.account.domain.model.AccountStatus;
import com.novabank.account.domain.model.AccountType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** One row of {@code accounts}; see V1 migration. */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "accounts")
public class AccountEntity {

  @Id private UUID id;

  @Column(name = "account_number", nullable = false, updatable = false, length = 12)
  private String accountNumber;

  @Column(name = "owner_user_id", nullable = false, updatable = false)
  private UUID ownerUserId;

  @Column(name = "customer_id", nullable = false, updatable = false)
  private UUID customerId;

  @Enumerated(EnumType.STRING)
  @Column(name = "type", nullable = false, updatable = false)
  private AccountType type;

  @Column(name = "currency", nullable = false, updatable = false, length = 3)
  private String currency;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false)
  private AccountStatus status;

  @Column(name = "opened_at", nullable = false, updatable = false)
  private Instant openedAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  /** {@code null} for a new account, so Spring Data persists instead of merging. */
  @Version private Long version;

  static AccountEntity of(
      UUID id,
      String accountNumber,
      UUID ownerUserId,
      UUID customerId,
      AccountType type,
      String currency,
      AccountStatus status,
      Instant openedAt,
      Instant updatedAt,
      Long version) {
    AccountEntity entity = new AccountEntity();
    entity.id = id;
    entity.accountNumber = accountNumber;
    entity.ownerUserId = ownerUserId;
    entity.customerId = customerId;
    entity.type = type;
    entity.currency = currency;
    entity.status = status;
    entity.openedAt = openedAt;
    entity.updatedAt = updatedAt;
    entity.version = version;
    return entity;
  }
}
