package com.novabank.ledger.infrastructure.messaging;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.novabank.ledger.application.command.PostLedgerTransactionCommand;
import com.novabank.ledger.application.command.PostLedgerTransactionCommand.Instruction;
import com.novabank.ledger.application.usecase.PostLedgerTransactionUseCase;
import com.novabank.ledger.domain.model.Direction;
import com.novabank.ledger.domain.model.TransactionType;
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
 * Takes {@code PostLedgerTransaction.v1} commands. A command that can't even be read goes to the
 * dead-letter topic; one that is readable but breaks a rule is answered with LedgerPostingFailed.
 */
@Component
public class LedgerCommandsListener {

  static final String POST_LEDGER_TRANSACTION = "PostLedgerTransaction.v1";

  private static final Logger LOGGER = LoggerFactory.getLogger(LedgerCommandsListener.class);

  private final PostLedgerTransactionUseCase postLedgerTransaction;
  private final JsonMapper jsonMapper;

  public LedgerCommandsListener(
      PostLedgerTransactionUseCase postLedgerTransaction, JsonMapper jsonMapper) {
    this.postLedgerTransaction = postLedgerTransaction;
    this.jsonMapper = jsonMapper;
  }

  @KafkaListener(topics = "${events.ledger-commands}")
  public void onCommand(ConsumerRecord<String, String> consumed) {
    String type = MessageHeaders.eventType(consumed);
    if (!POST_LEDGER_TRANSACTION.equals(type)) {
      throw new InvalidMessageException("Unknown ledger command " + type, null);
    }
    PostLedgerTransactionCommand command =
        toCommand(MessageHeaders.eventId(consumed), consumed.value());
    PostLedgerTransactionUseCase.Outcome outcome = postLedgerTransaction.post(command);
    LOGGER.info(
        "Ledger command {} for payment {}: {}", command.commandId(), command.paymentId(), outcome);
  }

  private PostLedgerTransactionCommand toCommand(UUID commandId, String json) {
    try {
      Payload payload = jsonMapper.readValue(json, Payload.class);
      if (payload.paymentId() == null || payload.type() == null || payload.postings() == null) {
        throw new InvalidMessageException("PostLedgerTransaction is missing fields", null);
      }
      List<Instruction> postings =
          payload.postings().stream()
              .map(p ->
                  new Instruction(
                      p.accountId(),
                      p.systemAccount(),
                      Direction.valueOf(p.direction()),
                      Money.of(p.amount(), p.currency())))
              .toList();
      return new PostLedgerTransactionCommand(
          commandId,
          payload.paymentId(),
          TransactionType.valueOf(payload.type()),
          postings,
          payload.reference());
    } catch (JacksonException | IllegalArgumentException | NullPointerException ex) {
      // Includes bad enum names and invalid amounts (InvalidMoneyException).
      throw new InvalidMessageException("PostLedgerTransaction is not valid", ex);
    }
  }

  @JsonIgnoreProperties(ignoreUnknown = true)
  record Payload(UUID paymentId, String type, List<PostingPayload> postings, String reference) {}

  @JsonIgnoreProperties(ignoreUnknown = true)
  record PostingPayload(
      UUID accountId, String systemAccount, String direction, String amount, String currency) {}
}
