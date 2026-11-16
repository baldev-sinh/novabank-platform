package com.novabank.payment.infrastructure.messaging;

import com.novabank.payment.application.port.SagaOutbox;
import com.novabank.payment.domain.model.Payment;
import com.novabank.payment.domain.model.PaymentStatus;
import com.novabank.payment.domain.model.PaymentType;
import com.novabank.platform.messaging.outbox.OutboxRecord;
import com.novabank.platform.messaging.outbox.OutboxWriter;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * The saga's messages, in the formats the account and ledger services take. All are keyed by
 * payment id, so one payment's messages stay in order on each topic.
 */
@Component
@RequiredArgsConstructor
public class KafkaSagaOutbox implements SagaOutbox {

  static final String AGGREGATE_TYPE = "Payment";

  private final OutboxWriter outboxWriter;
  private final EventsProperties events;
  private final Clock clock;

  @Override
  public void holdFunds(Payment payment) {
    send(
        events.accountCommands(),
        "HoldFunds.v1",
        payment,
        new HoldFunds(
            payment.id(),
            payment.fromAccountId(),
            payment.requestedBy(),
            payment.toAccountNumber(),
            payment.amount().amountString(),
            payment.amount().currencyCode()));
  }

  @Override
  public void releaseHold(Payment payment) {
    send(events.accountCommands(), "ReleaseHold.v1", payment, Map.of("paymentId", payment.id()));
  }

  /** Transfer: debit sender, credit beneficiary. Deposit: debit SETTLEMENT, credit the account. */
  @Override
  public void postLedgerTransaction(Payment payment) {
    String amount = payment.amount().amountString();
    String currency = payment.amount().currencyCode();
    Posting debit =
        payment.type() == PaymentType.TRANSFER
            ? new Posting(payment.fromAccountId(), null, "DEBIT", amount, currency)
            : new Posting(null, "SETTLEMENT", "DEBIT", amount, currency);
    Posting credit = new Posting(payment.toAccountId(), null, "CREDIT", amount, currency);
    send(
        events.ledgerCommands(),
        "PostLedgerTransaction.v1",
        payment,
        new PostLedgerTransaction(payment.id(), payment.type().name(), List.of(debit, credit)));
  }

  @Override
  public void paymentFinished(Payment payment) {
    boolean completed = payment.status() == PaymentStatus.COMPLETED;
    send(
        events.paymentEvents(),
        completed ? "PaymentCompleted.v1" : "PaymentFailed.v1",
        payment,
        new PaymentFinished(
            payment.id(),
            payment.type().name(),
            payment.requestedBy(),
            payment.fromAccountId(),
            payment.toAccountId(),
            payment.amount().amountString(),
            payment.amount().currencyCode(),
            payment.status().name(),
            payment.failureReason(),
            payment.ledgerTransactionId(),
            payment.updatedAt()));
  }

  private void send(String topic, String type, Payment payment, Object body) {
    Instant now = clock.instant();
    outboxWriter.append(
        new OutboxRecord(UUID.randomUUID(), topic, type, AGGREGATE_TYPE, payment.id(), body, now));
  }

  record HoldFunds(
      UUID paymentId,
      UUID accountId,
      UUID requestedBy,
      String beneficiaryAccountNumber,
      String amount,
      String currency) {}

  record Posting(
      UUID accountId, String systemAccount, String direction, String amount, String currency) {}

  record PostLedgerTransaction(UUID paymentId, String type, List<Posting> postings) {}

  record PaymentFinished(
      UUID paymentId,
      String type,
      UUID requestedBy,
      UUID fromAccountId,
      UUID toAccountId,
      String amount,
      String currency,
      String status,
      String failureReason,
      UUID ledgerTransactionId,
      Instant occurredAt) {}
}
