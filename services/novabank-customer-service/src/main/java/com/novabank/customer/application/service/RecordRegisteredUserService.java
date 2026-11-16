package com.novabank.customer.application.service;

import com.novabank.customer.application.port.messaging.KnownUserStore;
import com.novabank.customer.application.port.messaging.ProcessedEventStore;
import com.novabank.customer.application.usecase.RecordRegisteredUserUseCase;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class RecordRegisteredUserService implements RecordRegisteredUserUseCase {

  static final String EVENT_TYPE = "UserRegistered.v1";

  private final ProcessedEventStore processedEvents;
  private final KnownUserStore knownUsers;

  /**
   * One transaction for "mark processed" and the effect: either both happen or neither, so an event
   * is applied exactly once even though Kafka may deliver it more than once.
   */
  @Override
  @Transactional
  public boolean record(UUID eventId, UUID userId, String email, Instant registeredAt) {
    if (!processedEvents.markProcessed(eventId, EVENT_TYPE)) {
      return false;
    }
    knownUsers.save(userId, email, registeredAt);
    return true;
  }
}
