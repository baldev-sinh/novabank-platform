package com.novabank.account.infrastructure.messaging;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.novabank.account.application.command.LedgerPosting;
import com.novabank.account.application.usecase.ActivateAccountUseCase;
import com.novabank.account.application.usecase.FundsUseCase;
import com.novabank.platform.messaging.InvalidMessageException;
import com.novabank.platform.messaging.MessageHeaders;
import com.novabank.platform.money.Money;
import java.util.List;
import java.util.UUID;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

/**
 * Keeps the balance projection in step with the ledger, and activates accounts the ledger has
 * opened (6.4). LedgerPostingFailed is skipped: the saga answers it with ReleaseHold.
 */
@Component
public class LedgerEventsListener {

  static final String LEDGER_POSTED = "LedgerTransactionPosted.v1";
  static final String LEDGER_ACCOUNT_OPENED = "LedgerAccountOpened.v1";

  private static final Logger LOGGER = LoggerFactory.getLogger(LedgerEventsListener.class);

  private final FundsUseCase funds;
  private final ActivateAccountUseCase activation;
  private final JsonMapper jsonMapper;

  public LedgerEventsListener(
      FundsUseCase funds, ActivateAccountUseCase activation, JsonMapper jsonMapper) {
    this.funds = funds;
    this.activation = activation;
    this.jsonMapper = jsonMapper;
  }

  @KafkaListener(topics = "${events.ledger-events-topic}")
  public void onLedgerEvent(ConsumerRecord<String, String> consumed) {
    String type = MessageHeaders.eventType(consumed);
    if (LEDGER_ACCOUNT_OPENED.equals(type)) {
      UUID accountId = accountIdOf(consumed.value());
      boolean activated = activation.activate(MessageHeaders.eventId(consumed), accountId);
      LOGGER.info("Ledger opened account {}: {}", accountId, activated ? "ACTIVE" : "no change");
      return;
    }
    if (!LEDGER_POSTED.equals(type)) {
      return;
    }
    LedgerPosting posting = parse(MessageHeaders.eventId(consumed), consumed.value());
    FundsUseCase.Outcome outcome = funds.applyLedgerPosting(posting);
    LOGGER.info("Ledger posting for payment {}: {}", posting.paymentId(), outcome);
  }

  private LedgerPosting parse(UUID eventId, String json) {
    try {
      Payload payload = jsonMapper.readValue(json, Payload.class);
      List<LedgerPosting.Entry> entries =
          payload.entries().stream()
              .map(e ->
                  new LedgerPosting.Entry(
                      e.accountId(), e.sequence(), Money.of(e.balanceAfter(), e.currency())))
              .toList();
      return new LedgerPosting(eventId, payload.paymentId(), payload.transactionId(), entries);
    } catch (JacksonException | IllegalArgumentException | NullPointerException ex) {
      throw new InvalidMessageException("LedgerTransactionPosted is not valid", ex);
    }
  }

  private UUID accountIdOf(String json) {
    try {
      UUID accountId = jsonMapper.readValue(json, AccountOpenedPayload.class).accountId();
      if (accountId == null) {
        throw new InvalidMessageException("LedgerAccountOpened has no accountId", null);
      }
      return accountId;
    } catch (JacksonException | IllegalArgumentException ex) {
      throw new InvalidMessageException("LedgerAccountOpened is not valid", ex);
    }
  }

  @JsonIgnoreProperties(ignoreUnknown = true)
  record AccountOpenedPayload(UUID accountId) {}

  @JsonIgnoreProperties(ignoreUnknown = true)
  record Payload(UUID paymentId, UUID transactionId, List<EntryPayload> entries) {}

  @JsonIgnoreProperties(ignoreUnknown = true)
  record EntryPayload(UUID accountId, long sequence, String balanceAfter, String currency) {}
}
