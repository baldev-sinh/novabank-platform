package com.novabank.payment.infrastructure.messaging;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.novabank.payment.application.command.SagaReply;
import com.novabank.payment.application.usecase.SagaUseCase;
import com.novabank.platform.messaging.InvalidMessageException;
import com.novabank.platform.messaging.MessageHeaders;
import java.util.UUID;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

/** Replies from the account and ledger services. Other event types on those topics are skipped. */
@Component
public class SagaRepliesListener {

  private static final Logger LOGGER = LoggerFactory.getLogger(SagaRepliesListener.class);

  private final SagaUseCase saga;
  private final JsonMapper jsonMapper;

  public SagaRepliesListener(SagaUseCase saga, JsonMapper jsonMapper) {
    this.saga = saga;
    this.jsonMapper = jsonMapper;
  }

  @KafkaListener(topics = {"${events.account-events-topic}", "${events.ledger-events-topic}"})
  public void onReply(ConsumerRecord<String, String> consumed) {
    String type = MessageHeaders.eventType(consumed);
    if (!isReply(type)) {
      return; // e.g. AccountOpened
    }
    SagaReply reply = parse(type, MessageHeaders.eventId(consumed), consumed.value());
    SagaUseCase.Outcome outcome = saga.onReply(reply);
    LOGGER.debug("{} for payment {}: {}", type, reply.paymentId(), outcome);
  }

  private static boolean isReply(String type) {
    return switch (type == null ? "" : type) {
      case "FundsHeld.v1",
          "FundsHoldFailed.v1",
          "HoldReleased.v1",
          "BalancesUpdated.v1",
          "LedgerTransactionPosted.v1",
          "LedgerPostingFailed.v1" ->
          true;
      default -> false;
    };
  }

  private SagaReply parse(String type, UUID eventId, String json) {
    Payload p;
    try {
      p = jsonMapper.readValue(json, Payload.class);
    } catch (JacksonException | IllegalArgumentException ex) {
      throw new InvalidMessageException(type + " is not valid JSON", ex);
    }
    if (p.paymentId() == null) {
      throw new InvalidMessageException(type + " has no paymentId", null);
    }
    return switch (type) {
      case "FundsHeld.v1" ->
          new SagaReply.FundsHeld(
              eventId, p.paymentId(), required(p.holdId()), required(p.beneficiaryAccountId()));
      case "FundsHoldFailed.v1" ->
          new SagaReply.FundsHoldFailed(eventId, p.paymentId(), p.reason());
      case "HoldReleased.v1" -> new SagaReply.HoldReleased(eventId, p.paymentId(), p.outcome());
      case "BalancesUpdated.v1" ->
          new SagaReply.BalancesUpdated(eventId, p.paymentId(), p.transactionId());
      case "LedgerTransactionPosted.v1" ->
          new SagaReply.LedgerPosted(eventId, p.paymentId(), required(p.transactionId()));
      default -> new SagaReply.LedgerFailed(eventId, p.paymentId(), p.reason());
    };
  }

  private static UUID required(UUID value) {
    if (value == null) {
      throw new InvalidMessageException("Reply is missing a field", null);
    }
    return value;
  }

  /** The union of the reply fields this service needs; each reply type uses a few. */
  @JsonIgnoreProperties(ignoreUnknown = true)
  record Payload(
      UUID paymentId,
      UUID holdId,
      UUID beneficiaryAccountId,
      UUID transactionId,
      String reason,
      String outcome) {}
}
