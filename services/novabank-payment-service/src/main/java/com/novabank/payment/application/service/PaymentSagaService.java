package com.novabank.payment.application.service;

import com.novabank.payment.application.command.SagaReply;
import com.novabank.payment.application.port.PaymentStore;
import com.novabank.payment.application.port.PaymentStore.Overdue;
import com.novabank.payment.application.port.ProcessedEventStore;
import com.novabank.payment.application.port.SagaOutbox;
import com.novabank.payment.application.settings.SagaSettings;
import com.novabank.payment.application.usecase.SagaUseCase;
import com.novabank.payment.domain.model.Payment;
import com.novabank.payment.domain.model.PaymentStatus;
import com.novabank.payment.domain.model.PaymentType;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** The saga orchestrator (ADR-002): reacts to replies and drives the next step. */
@Service
@RequiredArgsConstructor
public class PaymentSagaService implements SagaUseCase {

  static final int SWEEP_BATCH = 50;

  /** Backoff between re-sends grows to at most stepTimeout × 2^5. */
  static final int MAX_BACKOFF_EXPONENT = 5;

  private static final Logger LOGGER = LoggerFactory.getLogger(PaymentSagaService.class);

  private final PaymentStore payments;
  private final ProcessedEventStore processedMessages;
  private final SagaOutbox outbox;
  private final SagaSettings settings;
  private final Clock clock;

  /**
   * Reply, state change and the next command commit together. The payment row is locked, so replies
   * for one payment arriving on two topics at once are handled one after the other.
   */
  @Override
  @Transactional
  public Outcome onReply(SagaReply reply) {
    if (!processedMessages.markProcessed(reply.eventId(), reply.type())) {
      return Outcome.DUPLICATE;
    }
    Optional<Payment> locked = payments.lock(reply.paymentId());
    if (locked.isEmpty()) {
      return Outcome.UNKNOWN_PAYMENT;
    }
    Payment payment = locked.get();
    if (!apply(payment, reply)) {
      LOGGER.debug(
          "Ignoring {} for payment {} in {}", reply.type(), payment.id(), payment.status());
      return Outcome.IGNORED;
    }
    payments.update(payment, clock.instant().plus(settings.stepTimeout()), true);
    sendStep(payment);
    LOGGER.info("Payment {} is now {}", payment.id(), payment.status());
    return Outcome.APPLIED;
  }

  private boolean apply(Payment payment, SagaReply reply) {
    return switch (reply) {
      case SagaReply.FundsHeld r ->
          payment.onFundsHeld(r.holdId(), r.beneficiaryAccountId(), clock);
      case SagaReply.FundsHoldFailed r -> payment.onFundsHoldFailed(r.reason(), clock);
      case SagaReply.LedgerPosted r -> payment.onLedgerPosted(r.transactionId(), clock);
      case SagaReply.LedgerFailed r -> payment.onLedgerFailed(r.reason(), clock);
      case SagaReply.BalancesUpdated r -> payment.onBalancesUpdated(r.transactionId(), clock);
      case SagaReply.HoldReleased r -> {
        if ("ALREADY_CAPTURED".equals(r.outcome())) {
          // Can't happen if the ledger's answers are final: flag it instead of guessing.
          LOGGER.error("Payment {}: hold already captured while compensating", payment.id());
          payments.recordAttempt(payment.id(), settings.maxAttempts(), clock.instant(), true);
          yield false;
        }
        yield payment.onHoldReleased(clock);
      }
    };
  }

  /**
   * The command for the payment's current state. Used after a transition and by the sweeper:
   * receivers answer the same for the same payment, so sending a step again is always safe.
   */
  private void sendStep(Payment payment) {
    switch (payment.status()) {
      case INITIATED -> {
        if (payment.type() == PaymentType.TRANSFER) {
          outbox.holdFunds(payment);
        } else {
          outbox.postLedgerTransaction(payment);
        }
      }
      // LEDGER_POSTED waits for BalancesUpdated; asking the ledger again makes it re-announce the
      // posting, which makes the account service re-announce the balances.
      case FUNDS_HELD, LEDGER_POSTED -> outbox.postLedgerTransaction(payment);
      case COMPENSATING -> outbox.releaseHold(payment);
      case COMPLETED, FAILED -> outbox.paymentFinished(payment);
    }
  }

  /**
   * Timeouts. A missing reply is never taken as failure: once funds are held, only the ledger knows
   * whether money moved, so the step is asked again until it answers. After {@code maxAttempts} the
   * payment is flagged for operations staff and asked less often.
   */
  @Override
  @Transactional
  public int resendOverdueSteps() {
    Instant now = clock.instant();
    List<Overdue> overdue = payments.lockOverdue(now, SWEEP_BATCH);
    for (Overdue item : overdue) {
      Payment payment = item.payment();
      int attempts = item.attempts() + 1;
      boolean needsAttention = attempts >= settings.maxAttempts();
      Duration wait =
          settings.stepTimeout().multipliedBy(1L << Math.min(attempts, MAX_BACKOFF_EXPONENT));
      payments.recordAttempt(payment.id(), attempts, now.plus(wait), needsAttention);
      if (payment.status() != PaymentStatus.COMPLETED && payment.status() != PaymentStatus.FAILED) {
        sendStep(payment);
      }
      if (needsAttention) {
        LOGGER.warn(
            "Payment {} stuck in {} after {} attempts", payment.id(), payment.status(), attempts);
      }
    }
    return overdue.size();
  }
}
