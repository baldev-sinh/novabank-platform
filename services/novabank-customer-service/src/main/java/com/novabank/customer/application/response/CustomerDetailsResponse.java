package com.novabank.customer.application.response;

import com.novabank.customer.domain.model.Customer;
import com.novabank.customer.domain.model.CustomerStatus;
import java.time.LocalDate;
import java.util.UUID;

public record CustomerDetailsResponse(
    UUID customerId,
    UUID userId,
    String firstName,
    String lastName,
    LocalDate dateOfBirth,
    String phone,
    CustomerStatus status) {

  public static CustomerDetailsResponse from(Customer customer) {
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
