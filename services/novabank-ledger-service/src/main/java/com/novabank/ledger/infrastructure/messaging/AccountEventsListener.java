package com.novabank.ledger.infrastructure.messaging;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.novabank.ledger.application.usecase.OpenLedgerAccountUseCase;
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

/** Creates the ledger side of each new customer account. Other account events are skipped. */
@Component
public class AccountEventsListener {

  static final String ACCOUNT_OPENED = "AccountOpened.v1";

  private static final Logger LOGGER = LoggerFactory.getLogger(AccountEventsListener.class);

  private final OpenLedgerAccountUseCase openLedgerAccount;
  private final JsonMapper jsonMapper;

  public AccountEventsListener(OpenLedgerAccountUseCase openLedgerAccount, JsonMapper jsonMapper) {
    this.openLedgerAccount = openLedgerAccount;
    this.jsonMapper = jsonMapper;
  }

  @KafkaListener(topics = "${events.account-events-topic}")
  public void onAccountEvent(ConsumerRecord<String, String> consumed) {
    if (!ACCOUNT_OPENED.equals(MessageHeaders.eventType(consumed))) {
      return;
    }
    UUID eventId = MessageHeaders.eventId(consumed);
    AccountOpenedPayload payload = parse(consumed.value());
    boolean applied =
        openLedgerAccount.open(
            eventId, payload.accountId(), payload.currency(), payload.occurredAt());
    LOGGER.info(
        "{} event {} for account {}",
        applied ? "Applied" : "Skipped duplicate",
        eventId,
        payload.accountId());
  }

  private AccountOpenedPayload parse(String json) {
    try {
      AccountOpenedPayload payload = jsonMapper.readValue(json, AccountOpenedPayload.class);
      if (payload.accountId() == null
          || payload.currency() == null
          || payload.occurredAt() == null) {
        throw new InvalidMessageException("AccountOpened payload is missing fields", null);
      }
      return payload;
    } catch (JacksonException | IllegalArgumentException ex) {
      throw new InvalidMessageException("AccountOpened payload is not valid JSON", ex);
    }
  }

  @JsonIgnoreProperties(ignoreUnknown = true)
  record AccountOpenedPayload(UUID accountId, String currency, Instant occurredAt) {}
}
