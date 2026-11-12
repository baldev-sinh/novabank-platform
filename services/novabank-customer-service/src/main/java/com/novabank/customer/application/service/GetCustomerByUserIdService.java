package com.novabank.customer.application.service;

import com.novabank.customer.application.exception.CustomerNotFoundException;
import com.novabank.customer.application.response.CustomerDetailsResponse;
import com.novabank.customer.application.usecase.GetCustomerByUserIdUseCase;
import com.novabank.customer.domain.model.Customer;
import com.novabank.customer.domain.repository.CustomerRepository;
import com.novabank.customer.domain.valueobject.identifier.UserId;
import java.util.Objects;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class GetCustomerByUserIdService implements GetCustomerByUserIdUseCase {

  private final CustomerRepository repository;

  @Override
  public CustomerDetailsResponse getByUserId(UUID userId) {
    Objects.requireNonNull(userId, "userId cannot be null");
    Customer customer =
        repository
            .findByUserId(new UserId(userId))
            .orElseThrow(() -> new CustomerNotFoundException("No customer profile for this user."));
    return new CustomerDetailsResponse(
        customer.id().value(),
        customer.userId().value(),
        customer.firstName(),
        customer.lastName(),
        customer.dateOfBirth(),
        customer.phone().value(),
        customer.status());
  }
}
