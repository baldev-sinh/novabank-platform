package com.novabank.customer.application.command;

import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

public record RegisterCustomerCommand(
    UUID userId, String firstName, String lastName, LocalDate dateOfBirth, String phone) {

  public RegisterCustomerCommand {
    Objects.requireNonNull(userId, "UserId cannot be null");
    Objects.requireNonNull(firstName, "First name cannot be null");
    Objects.requireNonNull(lastName, "Last name cannot be null");
    Objects.requireNonNull(dateOfBirth, "Date of birth cannot be null");
    Objects.requireNonNull(phone, "Phone cannot be null");
  }
}
