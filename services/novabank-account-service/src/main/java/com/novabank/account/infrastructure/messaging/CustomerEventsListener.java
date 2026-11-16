package com.novabank.account.infrastructure.messaging;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.novabank.account.application.usecase.RecordVerifiedCustomerUseCase;
import com.novabank.platform.messaging.InvalidMessageException;
import com.novabank.platform.messaging.MessageHeaders;
import java.time.Instant;
import java.util.UUID;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

/**
 * Consumes customer events. Unknown event types are skipped (the customer service may add new
 * ones); invalid events go to the dead-letter topic.
 */
@Component
public class CustomerEventsListener {

  static final String CUSTOMER_VERIFIED = "CustomerVerified.v1";

  private static final Logger LOGGER = LoggerFactory.getLogger(CustomerEventsListener.class);

  private final RecordVerifiedCustomerUseCase recordVerifiedCustomer;
  private final JsonMapper jsonMapper;

  public CustomerEventsListener(
      RecordVerifiedCustomerUseCase recordVerifiedCustomer, JsonMapper jsonMapper) {
    this.recordVerifiedCustomer = recordVerifiedCustomer;
    this.jsonMapper = jsonMapper;
  }

  @KafkaListener(topics = "${events.customer-events-topic}")
  public void onCustomerEvent(ConsumerRecord<String, String> consumed) {
    String eventType = MessageHeaders.eventType(consumed);
    if (!CUSTOMER_VERIFIED.equals(eventType)) {
      LOGGER.debug("Skipping event type {} at offset {}", eventType, consumed.offset());
      return;
    }

    UUID eventId = MessageHeaders.eventId(consumed);
    CustomerVerifiedPayload payload = parse(consumed.value());
    boolean applied =
        recordVerifiedCustomer.record(
            eventId, payload.customerId(), payload.userId(), payload.occurredAt());
    LOGGER.info(
        "{} event {} for customer {}",
        applied ? "Applied" : "Skipped duplicate",
        eventId,
        payload.customerId());
  }

  private CustomerVerifiedPayload parse(String json) {
    try {
      CustomerVerifiedPayload payload = jsonMapper.readValue(json, CustomerVerifiedPayload.class);
      if (payload.customerId() == null
          || payload.userId() == null
          || payload.occurredAt() == null) {
        throw new InvalidMessageException("CustomerVerified payload is missing fields", null);
      }
      return payload;
    } catch (JacksonException | IllegalArgumentException ex) {
      throw new InvalidMessageException("CustomerVerified payload is not valid JSON", ex);
    }
  }

  /** The part of the customer service's event this service needs; other fields are ignored. */
  @JsonIgnoreProperties(ignoreUnknown = true)
  record CustomerVerifiedPayload(UUID customerId, UUID userId, Instant occurredAt) {}
}
