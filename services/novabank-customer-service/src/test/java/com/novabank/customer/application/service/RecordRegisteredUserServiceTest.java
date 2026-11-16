package com.novabank.customer.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.novabank.customer.application.port.messaging.KnownUserStore;
import com.novabank.customer.application.port.messaging.ProcessedEventStore;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RecordRegisteredUserServiceTest {

  private static final UUID EVENT_ID = UUID.randomUUID();
  private static final UUID USER_ID = UUID.randomUUID();
  private static final Instant REGISTERED_AT = Instant.parse("2026-11-16T10:00:00Z");

  @Mock private ProcessedEventStore processedEvents;
  @Mock private KnownUserStore knownUsers;
  @InjectMocks private RecordRegisteredUserService service;

  @Test
  @DisplayName("A new event is marked processed and the user is stored")
  void recordsNewEvent() {
    when(processedEvents.markProcessed(EVENT_ID, "UserRegistered.v1")).thenReturn(true);

    boolean applied = service.record(EVENT_ID, USER_ID, "user@example.com", REGISTERED_AT);

    assertThat(applied).isTrue();
    verify(knownUsers).save(USER_ID, "user@example.com", REGISTERED_AT);
  }

  @Test
  @DisplayName("A redelivered event is skipped without touching the user store")
  void skipsDuplicateEvent() {
    when(processedEvents.markProcessed(any(), any())).thenReturn(false);

    boolean applied = service.record(EVENT_ID, USER_ID, "user@example.com", REGISTERED_AT);

    assertThat(applied).isFalse();
    verifyNoInteractions(knownUsers);
  }
}
