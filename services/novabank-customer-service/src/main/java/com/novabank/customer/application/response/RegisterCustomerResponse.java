package com.novabank.customer.application.response;

import com.novabank.customer.domain.model.CustomerStatus;
import java.util.UUID;

public record RegisterCustomerResponse(
    UUID customerId, UUID userId, String firstName, String lastName, CustomerStatus status) {}
