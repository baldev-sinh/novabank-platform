package com.novabank.customer.domain.repository;

import com.novabank.customer.domain.model.Customer;
import com.novabank.customer.domain.valueobject.identifier.CustomerId;
import com.novabank.customer.domain.valueobject.identifier.UserId;
import java.util.List;
import java.util.Optional;

public interface CustomerRepository {

  Customer save(Customer customer);

  Optional<Customer> findById(CustomerId customerId);

  Optional<Customer> findByUserId(UserId userId);

  boolean existsByUserId(UserId userId);

  /** Customers waiting for a KYC decision, oldest first. */
  List<Customer> findPendingKyc(int limit);
}
