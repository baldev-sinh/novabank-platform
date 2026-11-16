package com.novabank.account.application.service;

import com.novabank.account.application.port.customer.EligibleCustomerStore;
import com.novabank.account.application.port.messaging.ProcessedEventStore;
import com.novabank.account.application.usecase.RecordVerifiedCustomerUseCase;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class RecordVerifiedCustomerService implements RecordVerifiedCustomerUseCase {

  static final String EVENT_TYPE = "CustomerVerified.v1";

  private final ProcessedEventStore processedEvents;
  private final EligibleCustomerStore eligibleCustomers;

  /** "Mark processed" and the effect commit together: applied once despite redelivery. */
  @Override
  @Transactional
  public boolean record(UUID eventId, UUID customerId, UUID userId, Instant verifiedAt) {
    if (!processedEvents.markProcessed(eventId, EVENT_TYPE)) {
      return false;
    }
    eligibleCustomers.save(customerId, userId, verifiedAt);
    return true;
  }
}
