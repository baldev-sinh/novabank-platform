package com.novabank.customer.application.service;

import com.novabank.customer.application.command.RegisterCustomerCommand;
import com.novabank.customer.application.exception.DuplicateCustomerException;
import com.novabank.customer.application.response.RegisterCustomerResponse;
import com.novabank.customer.application.usecase.RegisterCustomerUseCase;
import com.novabank.customer.domain.model.Customer;
import com.novabank.customer.domain.repository.CustomerRepository;
import com.novabank.customer.domain.valueobject.identifier.UserId;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
@RequiredArgsConstructor
public class RegisterCustomerService implements RegisterCustomerUseCase {

  private final CustomerRepository repository;

  @Override
  public RegisterCustomerResponse register(RegisterCustomerCommand command) {

    Objects.requireNonNull(command, "command cannot be null");

    UserId userId = new UserId(command.userId());

    if (repository.existsByUserId(userId)) {
      throw new DuplicateCustomerException("Customer already exists for user: " + command.userId());
    }

    Customer customer =
        Customer.register(
            userId,
            command.firstName(),
            command.lastName(),
            command.dateOfBirth(),
            command.phone());

    Customer savedCustomer = repository.save(customer);

    return buildResponse(savedCustomer);
  }

  private RegisterCustomerResponse buildResponse(Customer customer) {

    Objects.requireNonNull(customer, "customer cannot be null");

    return new RegisterCustomerResponse(
        customer.id().value(),
        customer.userId().value(),
        customer.firstName(),
        customer.lastName(),
        customer.status());
  }
}
