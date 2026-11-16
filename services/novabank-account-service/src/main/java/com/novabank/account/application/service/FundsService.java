package com.novabank.account.application.service;

import com.novabank.account.application.command.HoldFundsCommand;
import com.novabank.account.application.command.LedgerPosting;
import com.novabank.account.application.event.BalancesUpdatedEvent;
import com.novabank.account.application.event.FundsHeldEvent;
import com.novabank.account.application.event.FundsHoldFailedEvent;
import com.novabank.account.application.event.HoldReleasedEvent;
import com.novabank.account.application.port.funds.AccountFundsStore;
import com.novabank.account.application.port.funds.HoldRejectionStore;
import com.novabank.account.application.port.funds.HoldStore;
import com.novabank.account.application.port.messaging.AccountEventOutbox;
import com.novabank.account.application.port.messaging.ProcessedEventStore;
import com.novabank.account.application.usecase.FundsUseCase;
import com.novabank.account.domain.exception.DomainException;
import com.novabank.account.domain.exception.HoldRejectedException;
import com.novabank.account.domain.model.AccountFunds;
import com.novabank.account.domain.model.AccountFunds.Beneficiary;
import com.novabank.account.domain.model.Hold;
import com.novabank.account.domain.model.HoldRejectionReason;
import com.novabank.account.domain.model.HoldStatus;
import com.novabank.account.domain.valueobject.AccountNumber;
import java.time.Clock;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class FundsService implements FundsUseCase {

  static final String HOLD_FUNDS = "HoldFunds.v1";
  static final String RELEASE_HOLD = "ReleaseHold.v1";
  static final String LEDGER_POSTED = "LedgerTransactionPosted.v1";

  private final ProcessedEventStore processedMessages;
  private final AccountFundsStore funds;
  private final HoldStore holds;
  private final HoldRejectionStore rejections;
  private final AccountEventOutbox events;
  private final Clock clock;

  /** The sender's funds row is locked, so concurrent holds can't spend the same money. */
  @Override
  @Transactional
  public Outcome hold(HoldFundsCommand command) {
    if (!processedMessages.markProcessed(command.commandId(), HOLD_FUNDS)) {
      return Outcome.DUPLICATE;
    }
    // The outcome per payment is final: a re-sent command gets the same answer.
    Optional<Hold> existing = holds.findByPaymentId(command.paymentId());
    if (existing.isPresent()) {
      events.add(fundsHeld(existing.get()));
      return Outcome.APPLIED;
    }
    Optional<HoldRejectionReason> refused = rejections.find(command.paymentId());
    if (refused.isPresent()) {
      events.add(failed(command.paymentId(), refused.get()));
      return Outcome.REJECTED;
    }

    try {
      Beneficiary beneficiary = beneficiaryOf(command.beneficiaryAccountNumber());
      AccountFunds sender =
          funds
              .lock(command.accountId())
              .orElseThrow(() -> new HoldRejectedException(HoldRejectionReason.ACCOUNT_NOT_FOUND));
      Hold hold =
          sender.placeHold(
              command.paymentId(), command.requestedBy(), beneficiary, command.amount(), clock);
      holds.save(hold);
      events.add(fundsHeld(hold));
      return Outcome.APPLIED;
    } catch (HoldRejectedException ex) {
      rejections.save(command.paymentId(), ex.reason(), clock.instant());
      events.add(failed(command.paymentId(), ex.reason()));
      return Outcome.REJECTED;
    }
  }

  /** Compensation. Always answers, so the saga can finish even if nothing was held. */
  @Override
  @Transactional
  public Outcome release(UUID commandId, UUID paymentId) {
    if (!processedMessages.markProcessed(commandId, RELEASE_HOLD)) {
      return Outcome.DUPLICATE;
    }
    Optional<Hold> found = holds.findByPaymentId(paymentId);
    String outcome;
    if (found.isEmpty()) {
      outcome = "NO_HOLD";
    } else {
      Hold hold = found.get();
      // Lock the account so the release can't interleave with a capture of the same hold.
      funds.lock(hold.accountId());
      Hold current = holds.findByPaymentId(paymentId).orElseThrow();
      if (current.isActive()) {
        current.release();
        holds.updateStatus(current);
        outcome = "RELEASED";
      } else {
        outcome = current.status() == HoldStatus.CAPTURED ? "ALREADY_CAPTURED" : "RELEASED";
      }
    }
    events.add(HoldReleasedEvent.of(paymentId, outcome, clock.instant()));
    return Outcome.APPLIED;
  }

  /**
   * Moves the projection forward and captures the hold in ONE transaction: the balance drops and
   * the reservation disappears together, so available money is never counted twice.
   */
  @Override
  @Transactional
  public Outcome applyLedgerPosting(LedgerPosting posting) {
    if (!processedMessages.markProcessed(posting.eventId(), LEDGER_POSTED)) {
      return Outcome.DUPLICATE;
    }
    List<UUID> ours = new ArrayList<>();
    // Same lock order as everywhere else (by id), so two postings can't deadlock.
    List<LedgerPosting.Entry> entries =
        posting.entries().stream()
            .sorted(Comparator.comparing(LedgerPosting.Entry::accountId))
            .toList();
    for (LedgerPosting.Entry entry : entries) {
      Optional<AccountFunds> account = funds.lock(entry.accountId());
      if (account.isEmpty()) {
        continue; // e.g. the bank's SETTLEMENT account: not an account of this service
      }
      AccountFunds locked = account.get();
      if (locked.applyLedgerEntry(entry.sequence(), entry.balanceAfter())) {
        funds.saveProjection(locked);
      }
      ours.add(entry.accountId());
    }
    if (ours.isEmpty()) {
      return Outcome.IGNORED;
    }
    holds
        .findByPaymentId(posting.paymentId())
        .filter(Hold::isActive)
        .ifPresent(hold -> {
          hold.capture();
          holds.updateStatus(hold);
        });
    events.add(
        BalancesUpdatedEvent.of(
            posting.paymentId(), posting.transactionId(), ours, clock.instant()));
    return Outcome.APPLIED;
  }

  private Beneficiary beneficiaryOf(String accountNumber) {
    AccountNumber number;
    try {
      number = AccountNumber.of(accountNumber);
    } catch (DomainException ex) {
      throw new HoldRejectedException(HoldRejectionReason.BENEFICIARY_NOT_FOUND);
    }
    return funds
        .findBeneficiary(number)
        .orElseThrow(() -> new HoldRejectedException(HoldRejectionReason.BENEFICIARY_NOT_FOUND));
  }

  private FundsHeldEvent fundsHeld(Hold hold) {
    return FundsHeldEvent.of(
        hold.paymentId(),
        hold.id(),
        hold.accountId(),
        hold.beneficiaryAccountId(),
        hold.amount().amountString(),
        hold.amount().currencyCode(),
        clock.instant());
  }

  private FundsHoldFailedEvent failed(UUID paymentId, HoldRejectionReason reason) {
    return FundsHoldFailedEvent.of(paymentId, reason.name(), clock.instant());
  }
}
