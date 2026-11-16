package com.novabank.customer.infrastructure.persistence.customer.repository;

import com.novabank.customer.domain.model.CustomerStatus;
import com.novabank.customer.infrastructure.persistence.customer.entity.CustomerEntity;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SpringDataCustomerRepository extends JpaRepository<CustomerEntity, UUID> {

  Optional<CustomerEntity> findByUserId(UUID userId);

  boolean existsByUserId(UUID userId);

  List<CustomerEntity> findByStatusOrderByCreatedAtAsc(CustomerStatus status, Limit limit);
}
