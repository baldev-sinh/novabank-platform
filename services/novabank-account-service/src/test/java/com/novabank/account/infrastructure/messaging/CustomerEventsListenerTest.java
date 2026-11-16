package com.novabank.account.infrastructure.messaging;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.novabank.account.application.usecase.RecordVerifiedCustomerUseCase;
import com.novabank.platform.messaging.InvalidMessageException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.json.JsonMapper;

@ExtendWith(MockitoExtension.class)
class CustomerEventsListenerTest {

  private static final UUID EVENT_ID = UUID.randomUUID();
  private static final UUID CUSTOMER_ID = UUID.randomUUID();
  private static final UUID USER_ID = UUID.randomUUID();

  @Mock private RecordVerifiedCustomerUseCase recordVerifiedCustomer;

  private CustomerEventsListener listener() {
    return new CustomerEventsListener(recordVerifiedCustomer, JsonMapper.builder().build());
  }

  @Test
  @DisplayName("CustomerVerified is recorded; extra fields such as verifiedBy are ignored")
  void recordsCustomerVerified() {
    String payload =
        ("{\"eventId\":\"%s\",\"customerId\":\"%s\",\"userId\":\"%s\",\"verifiedBy\":\"%s\","
                + "\"occurredAt\":\"2026-11-16T10:00:00Z\"}")
            .formatted(EVENT_ID, CUSTOMER_ID, USER_ID, UUID.randomUUID());

    listener().onCustomerEvent(record("CustomerVerified.v1", payload));

    verify(recordVerifiedCustomer)
        .record(EVENT_ID, CUSTOMER_ID, USER_ID, Instant.parse("2026-11-16T10:00:00Z"));
  }

  @Test
  @DisplayName("Other customer event types are skipped")
  void skipsOtherTypes() {
    listener().onCustomerEvent(record("CustomerSuspended.v1", "not json"));

    verifyNoInteractions(recordVerifiedCustomer);
  }

  @Test
  @DisplayName("Bad JSON and missing fields are invalid (straight to the dead-letter topic)")
  void rejectsInvalidPayloads() {
    assertThatThrownBy(() -> listener().onCustomerEvent(record("CustomerVerified.v1", "{")))
        .isInstanceOf(InvalidMessageException.class);
    assertThatThrownBy(() ->
            listener()
                .onCustomerEvent(
                    record(
                        "CustomerVerified.v1", "{\"customerId\":\"%s\"}".formatted(CUSTOMER_ID))))
        .isInstanceOf(InvalidMessageException.class);
  }

  private static ConsumerRecord<String, String> record(String eventType, String value) {
    ConsumerRecord<String, String> consumed =
        new ConsumerRecord<>("customer.events.v1", 0, 0L, CUSTOMER_ID.toString(), value);
    consumed.headers().add("eventType", eventType.getBytes(StandardCharsets.UTF_8));
    consumed.headers().add("eventId", EVENT_ID.toString().getBytes(StandardCharsets.UTF_8));
    return consumed;
  }
}
