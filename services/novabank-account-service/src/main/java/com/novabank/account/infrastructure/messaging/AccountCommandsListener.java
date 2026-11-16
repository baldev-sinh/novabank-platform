package com.novabank.account.infrastructure.messaging;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.novabank.account.application.command.HoldFundsCommand;
import com.novabank.account.application.usecase.FundsUseCase;
import com.novabank.platform.messaging.InvalidMessageException;
import com.novabank.platform.messaging.MessageHeaders;
import com.novabank.platform.money.Money;
import java.util.UUID;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

/** HoldFunds and ReleaseHold from the payment saga. Unreadable commands go to the DLT. */
@Component
public class AccountCommandsListener {

  static final String HOLD_FUNDS = "HoldFunds.v1";
  static final String RELEASE_HOLD = "ReleaseHold.v1";

  private static final Logger LOGGER = LoggerFactory.getLogger(AccountCommandsListener.class);

  private final FundsUseCase funds;
  private final JsonMapper jsonMapper;

  public AccountCommandsListener(FundsUseCase funds, JsonMapper jsonMapper) {
    this.funds = funds;
    this.jsonMapper = jsonMapper;
  }

  @KafkaListener(topics = "${events.account-commands.topic}")
  public void onCommand(ConsumerRecord<String, String> consumed) {
    String type = MessageHeaders.eventType(consumed);
    UUID commandId = MessageHeaders.eventId(consumed);
    FundsUseCase.Outcome outcome;
    UUID paymentId;
    if (HOLD_FUNDS.equals(type)) {
      HoldFundsCommand command = holdFunds(commandId, consumed.value());
      paymentId = command.paymentId();
      outcome = funds.hold(command);
    } else if (RELEASE_HOLD.equals(type)) {
      paymentId = read(consumed.value(), ReleasePayload.class).paymentId();
      if (paymentId == null) {
        throw new InvalidMessageException("ReleaseHold is missing paymentId", null);
      }
      outcome = funds.release(commandId, paymentId);
    } else {
      throw new InvalidMessageException("Unknown account command " + type, null);
    }
    LOGGER.info("{} for payment {}: {}", type, paymentId, outcome);
  }

  private HoldFundsCommand holdFunds(UUID commandId, String json) {
    HoldPayload p = read(json, HoldPayload.class);
    try {
      return new HoldFundsCommand(
          commandId,
          p.paymentId(),
          p.accountId(),
          p.requestedBy(),
          p.beneficiaryAccountNumber(),
          Money.of(p.amount(), p.currency()));
    } catch (IllegalArgumentException | NullPointerException ex) {
      throw new InvalidMessageException("HoldFunds is not valid", ex);
    }
  }

  private <T> T read(String json, Class<T> type) {
    try {
      return jsonMapper.readValue(json, type);
    } catch (JacksonException | IllegalArgumentException ex) {
      throw new InvalidMessageException("Command is not valid JSON", ex);
    }
  }

  @JsonIgnoreProperties(ignoreUnknown = true)
  record HoldPayload(
      UUID paymentId,
      UUID accountId,
      UUID requestedBy,
      String beneficiaryAccountNumber,
      String amount,
      String currency) {}

  @JsonIgnoreProperties(ignoreUnknown = true)
  record ReleasePayload(UUID paymentId) {}
}
