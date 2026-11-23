package com.novabank.notification.domain;

import java.util.Locale;
import java.util.Map;

/**
 * The email texts. Account numbers are masked to their last four digits: an email can be forwarded,
 * read on a shared screen or kept in another provider's mailbox.
 */
public final class NotificationTemplates {

  /** Keys of {@link Notification#details()}. */
  public static final String AMOUNT = "amount";

  public static final String CURRENCY = "currency";
  public static final String FAILURE_REASON = "failureReason";

  private static final String FOOTER =
      """

      This is an automated message from NovaBank. We will never ask for your password or a \
      one-time code by email.
      """;

  private NotificationTemplates() {}

  /**
   * @param accountNumber the account the email is about (beneficiary, credited or new account);
   *     {@code null} if not known yet
   */
  public static EmailMessage render(
      NotificationKind kind, Map<String, String> details, String accountNumber) {
    String money = details.get(AMOUNT) + " " + details.get(CURRENCY);
    String account = accountNumber == null ? "your account" : "account " + mask(accountNumber);
    return switch (kind) {
      case TRANSFER_SENT ->
          new EmailMessage(
              "Transfer sent",
              "You sent %s to %s.%n".formatted(money, beneficiary(accountNumber)) + FOOTER);
      case TRANSFER_FAILED ->
          new EmailMessage(
              "Transfer not completed",
              "Your transfer of %s was not completed (%s). No money left your account.%n"
                      .formatted(money, reason(details.get(FAILURE_REASON)))
                  + FOOTER);
      case MONEY_RECEIVED ->
          new EmailMessage(
              "Money received", "%s arrived in %s.%n".formatted(money, account) + FOOTER);
      case ACCOUNT_READY ->
          new EmailMessage(
              "Your NovaBank account is ready",
              (accountNumber == null
                      ? "Your new %s account is open and ready to use.%n"
                          .formatted(details.get(CURRENCY))
                      : "Your new %s account %s is open and ready to use.%n"
                          .formatted(details.get(CURRENCY), mask(accountNumber)))
                  + FOOTER);
    };
  }

  /** Last four characters only, e.g. {@code •••• 4821}. */
  public static String mask(String accountNumber) {
    String last = accountNumber.substring(Math.max(0, accountNumber.length() - 4));
    return "•••• " + last;
  }

  private static String beneficiary(String accountNumber) {
    return accountNumber == null ? "the beneficiary account" : "account " + mask(accountNumber);
  }

  /** INSUFFICIENT_FUNDS → "insufficient funds". */
  private static String reason(String failureReason) {
    if (failureReason == null || failureReason.isBlank()) {
      return "it could not be processed";
    }
    return failureReason.toLowerCase(Locale.ROOT).replace('_', ' ');
  }
}
