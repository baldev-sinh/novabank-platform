package com.novabank.ledger.application.service;

import com.novabank.ledger.application.command.PostLedgerTransactionCommand;
import com.novabank.ledger.application.command.PostLedgerTransactionCommand.Instruction;
import com.novabank.ledger.application.event.LedgerPostingFailedEvent;
import com.novabank.ledger.application.event.LedgerTransactionPostedEvent;
import com.novabank.ledger.application.port.LedgerAccountStore;
import com.novabank.ledger.application.port.LedgerEventOutbox;
import com.novabank.ledger.application.port.LedgerTransactionStore;
import com.novabank.ledger.application.port.ProcessedEventStore;
import com.novabank.ledger.application.port.RejectedPostingStore;
import com.novabank.ledger.application.usecase.PostLedgerTransactionUseCase;
import com.novabank.ledger.domain.exception.PostingRejectedException;
import com.novabank.ledger.domain.model.LedgerAccount;
import com.novabank.ledger.domain.model.LedgerTransaction;
import com.novabank.ledger.domain.model.PostedEntry;
import com.novabank.ledger.domain.model.Posting;
import com.novabank.ledger.domain.model.RejectionReason;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PostLedgerTransactionService implements PostLedgerTransactionUseCase {

  static final String COMMAND_TYPE = "PostLedgerTransaction.v1";

  private final ProcessedEventStore processedMessages;
  private final LedgerAccountStore accounts;
  private final LedgerTransactionStore transactions;
  private final RejectedPostingStore rejections;
  private final LedgerEventOutbox events;
  private final Clock clock;

  /**
   * One database transaction: mark the message, lock the accounts, post (or record the refusal),
   * and write the reply event. A refusal commits too; it is a business outcome, not an error.
   */
  @Override
  @Transactional
  public Outcome post(PostLedgerTransactionCommand command) {
    if (!processedMessages.markProcessed(command.commandId(), COMMAND_TYPE)) {
      return Outcome.DUPLICATE;
    }
    Optional<Outcome> earlier = replyWithEarlierOutcome(command.paymentId());
    if (earlier.isPresent()) {
      return earlier.get();
    }

    try {
      LedgerTransaction transaction =
          LedgerTransaction.create(
              command.paymentId(),
              command.type(),
              resolve(command.postings()),
              command.reference(),
              clock);
      List<PostedEntry> entries = apply(transaction);
      transactions.save(transaction, entries);
      events.add(
          LedgerTransactionPostedEvent.of(
              transaction.paymentId(),
              transaction.id(),
              transaction.type().name(),
              transaction.reference(),
              transaction.postedAt(),
              entries));
      return Outcome.POSTED;
    } catch (PostingRejectedException ex) {
      // Nothing was written yet: the domain checks run before any store call.
      rejections.save(command.paymentId(), ex.reason(), clock.instant());
      events.add(
          LedgerPostingFailedEvent.of(command.paymentId(), ex.reason().name(), clock.instant()));
      return Outcome.REJECTED;
    }
  }

  /**
   * A re-sent command (e.g. after a saga timeout) gets the stored answer again, so the payment saga
   * always hears the same final outcome.
   */
  private Optional<Outcome> replyWithEarlierOutcome(UUID paymentId) {
    Optional<LedgerTransactionStore.Stored> posted = transactions.findByPaymentId(paymentId);
    if (posted.isPresent()) {
      LedgerTransactionStore.Stored stored = posted.get();
      events.add(
          LedgerTransactionPostedEvent.of(
              paymentId,
              stored.transactionId(),
              stored.type(),
              stored.reference(),
              stored.postedAt(),
              stored.entries()));
      return Optional.of(Outcome.POSTED);
    }
    return rejections
        .find(paymentId)
        .map(reason -> {
          events.add(LedgerPostingFailedEvent.of(paymentId, reason.name(), clock.instant()));
          return Outcome.REJECTED;
        });
  }

  private List<Posting> resolve(List<Instruction> instructions) {
    List<Posting> postings = new ArrayList<>();
    for (Instruction instruction : instructions) {
      postings.add(
          new Posting(accountIdOf(instruction), instruction.direction(), instruction.amount()));
    }
    return postings;
  }

  private UUID accountIdOf(Instruction instruction) {
    boolean customer = instruction.accountId() != null;
    boolean system = instruction.systemAccount() != null;
    if (customer == system) {
      throw new PostingRejectedException(
          RejectionReason.INVALID_POSTINGS,
          "Each posting needs exactly one of accountId or systemAccount.");
    }
    if (customer) {
      return instruction.accountId();
    }
    return accounts
        .findSystemAccountId(instruction.systemAccount(), instruction.amount().currency())
        .orElseThrow(() ->
            new PostingRejectedException(
                RejectionReason.UNKNOWN_ACCOUNT,
                "No "
                    + instruction.systemAccount()
                    + " account in "
                    + instruction.amount().currencyCode()
                    + "."));
  }

  private List<PostedEntry> apply(LedgerTransaction transaction) {
    List<UUID> ids = transaction.postings().stream().map(Posting::accountId).toList();
    Map<UUID, LedgerAccount> locked =
        accounts.lockForUpdate(ids).stream()
            .collect(Collectors.toMap(LedgerAccount::id, Function.identity()));
    if (locked.size() != ids.size()) {
      throw new PostingRejectedException(
          RejectionReason.UNKNOWN_ACCOUNT, "One or more accounts don't exist in the ledger.");
    }
    List<PostedEntry> entries = new ArrayList<>();
    for (Posting posting : transaction.postings()) {
      entries.add(locked.get(posting.accountId()).apply(posting));
    }
    accounts.updateBalances(locked.values());
    return entries;
  }
}
