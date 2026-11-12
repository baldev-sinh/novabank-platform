package com.novabank.customer.infrastructure.persistence.customer.adapter;

import com.novabank.customer.application.exception.DuplicateCustomerException;
import com.novabank.customer.domain.model.Customer;
import com.novabank.customer.domain.repository.CustomerRepository;
import com.novabank.customer.domain.valueobject.PhoneNumber;
import com.novabank.customer.domain.valueobject.identifier.CustomerId;
import com.novabank.customer.domain.valueobject.identifier.UserId;
import com.novabank.customer.infrastructure.persistence.customer.entity.CustomerEntity;
import com.novabank.customer.infrastructure.persistence.customer.repository.SpringDataCustomerRepository;
import java.util.Objects;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class JpaCustomerRepositoryAdapter implements CustomerRepository {

  /** Unique constraint on {@code customers.user_id} (see V1 migration). */
  static final String USER_ID_UNIQUE_CONSTRAINT = "uk_customers_user_id";

  private final SpringDataCustomerRepository repository;

  @Override
  public Customer save(Customer customer) {
    Objects.requireNonNull(customer, "Customer cannot be null");
    try {
      // Flush now so a unique violation surfaces here, not later at commit time.
      return toDomain(repository.saveAndFlush(toEntity(customer)));
    } catch (DataIntegrityViolationException ex) {
      // Two concurrent requests can both pass existsByUserId; the constraint is the real guard.
      if (violates(ex, USER_ID_UNIQUE_CONSTRAINT)) {
        throw new DuplicateCustomerException(
            "Customer already exists for user: " + customer.userId().value());
      }
      throw ex;
    }
  }

  @Override
  public Optional<Customer> findById(CustomerId customerId) {
    Objects.requireNonNull(customerId, "CustomerId cannot be null");
    return repository.findById(customerId.value()).map(JpaCustomerRepositoryAdapter::toDomain);
  }

  @Override
  public Optional<Customer> findByUserId(UserId userId) {
    Objects.requireNonNull(userId, "UserId cannot be null");
    return repository.findByUserId(userId.value()).map(JpaCustomerRepositoryAdapter::toDomain);
  }

  @Override
  public boolean existsByUserId(UserId userId) {
    Objects.requireNonNull(userId, "UserId cannot be null");
    return repository.existsByUserId(userId.value());
  }

  private static boolean violates(DataIntegrityViolationException ex, String constraintName) {
    for (Throwable cause = ex; cause != null; cause = cause.getCause()) {
      if (cause instanceof ConstraintViolationException violation) {
        return constraintName.equalsIgnoreCase(violation.getConstraintName());
      }
    }
    return false;
  }

  private static CustomerEntity toEntity(Customer customer) {
    return CustomerEntity.of(
        customer.id().value(),
        customer.userId().value(),
        customer.firstName(),
        customer.lastName(),
        customer.dateOfBirth(),
        customer.phone().value(),
        customer.status(),
        customer.createdAt(),
        customer.updatedAt(),
        customer.version());
  }

  private static Customer toDomain(CustomerEntity entity) {
    return Customer.restore(
        new CustomerId(entity.getId()),
        new UserId(entity.getUserId()),
        entity.getFirstName(),
        entity.getLastName(),
        entity.getDateOfBirth(),
        PhoneNumber.of(entity.getPhone()),
        entity.getStatus(),
        entity.getCreatedAt(),
        entity.getUpdatedAt(),
        entity.getVersion());
  }
}
