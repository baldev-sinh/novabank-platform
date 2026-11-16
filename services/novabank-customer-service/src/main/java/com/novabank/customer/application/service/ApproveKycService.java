package com.novabank.customer.application.service;

import com.novabank.customer.application.event.CustomerVerifiedEvent;
import com.novabank.customer.application.exception.CustomerNotFoundException;
import com.novabank.customer.application.exception.CustomerStateConflictException;
import com.novabank.customer.application.port.compliance.KycDecisionLog;
import com.novabank.customer.application.port.messaging.CustomerEventOutbox;
import com.novabank.customer.application.response.CustomerDetailsResponse;
import com.novabank.customer.application.usecase.ApproveKycUseCase;
import com.novabank.customer.domain.model.Customer;
import com.novabank.customer.domain.model.CustomerStatus;
import com.novabank.customer.domain.repository.CustomerRepository;
import com.novabank.customer.domain.valueobject.identifier.CustomerId;
import java.time.Clock;
import java.util.Objects;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ApproveKycService implements ApproveKycUseCase {

  private final CustomerRepository customers;
  private final KycDecisionLog decisions;
  private final CustomerEventOutbox events;
  private final Clock clock;

  /**
   * Status change, audit record and event commit together. Concurrent approvals are serialised by
   * the customer's optimistic lock: the loser fails instead of publishing a second event.
   */
  @Override
  @Transactional
  public CustomerDetailsResponse approve(UUID customerId, UUID approvedBy) {
    Objects.requireNonNull(customerId, "customerId cannot be null");
    Objects.requireNonNull(approvedBy, "approvedBy cannot be null");

    Customer customer =
        customers
            .findById(new CustomerId(customerId))
            .orElseThrow(() -> new CustomerNotFoundException("Customer not found."));

    if (customer.status() == CustomerStatus.ACTIVE) {
      // A retried approval: already done, nothing to change or publish.
      return CustomerDetailsResponse.from(customer);
    }
    if (customer.status() != CustomerStatus.PENDING_KYC) {
      throw new CustomerStateConflictException(
          "KYC can't be approved for a customer in status " + customer.status() + ".");
    }

    customer.approveKyc(clock);
    Customer saved = customers.save(customer);

    decisions.recordApproval(customerId, approvedBy, saved.updatedAt());
    events.add(
        CustomerVerifiedEvent.of(
            customerId, saved.userId().value(), approvedBy, saved.updatedAt()));

    return CustomerDetailsResponse.from(saved);
  }
}
