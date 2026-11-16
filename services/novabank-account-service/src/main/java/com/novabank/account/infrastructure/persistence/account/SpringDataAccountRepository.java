package com.novabank.account.infrastructure.persistence.account;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface SpringDataAccountRepository extends JpaRepository<AccountEntity, UUID> {

  List<AccountEntity> findByOwnerUserIdOrderByOpenedAtAsc(UUID ownerUserId);

  boolean existsByAccountNumber(String accountNumber);
}
