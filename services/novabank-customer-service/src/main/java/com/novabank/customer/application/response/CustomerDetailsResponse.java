package com.novabank.customer.application.response;

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
    CustomerStatus status) {}
