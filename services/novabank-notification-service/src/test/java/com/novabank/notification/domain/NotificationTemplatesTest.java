package com.novabank.notification.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class NotificationTemplatesTest {

  private static final Map<String, String> MONEY = Map.of("amount", "120.00", "currency", "INR");

  @Test
  @DisplayName("Account numbers are masked to their last four digits")
  void masksAccountNumbers() {
    EmailMessage email =
        NotificationTemplates.render(NotificationKind.TRANSFER_SENT, MONEY, "100000004821");

    assertThat(email.subject()).isEqualTo("Transfer sent");
    assertThat(email.text()).contains("You sent 120.00 INR to account •••• 4821.");
    assertThat(email.text()).doesNotContain("100000004821");
  }

  @Test
  @DisplayName("Each kind has its own subject and text; unknown numbers read naturally")
  void everyKind() {
    assertThat(NotificationTemplates.render(NotificationKind.TRANSFER_SENT, MONEY, null).text())
        .contains("to the beneficiary account");
    assertThat(
            NotificationTemplates.render(NotificationKind.MONEY_RECEIVED, MONEY, "200000001234")
                .text())
        .contains("120.00 INR arrived in account •••• 1234.");
    assertThat(NotificationTemplates.render(NotificationKind.MONEY_RECEIVED, MONEY, null).text())
        .contains("arrived in your account");
    assertThat(
            NotificationTemplates.render(
                    NotificationKind.ACCOUNT_READY, Map.of("currency", "EUR"), "300000009999")
                .text())
        .contains("Your new EUR account •••• 9999 is open and ready to use.");
    assertThat(
            NotificationTemplates.render(
                    NotificationKind.ACCOUNT_READY, Map.of("currency", "EUR"), null)
                .subject())
        .isEqualTo("Your NovaBank account is ready");
  }

  @Test
  @DisplayName("A failed transfer explains the reason in words")
  void failureReason() {
    EmailMessage email =
        NotificationTemplates.render(
            NotificationKind.TRANSFER_FAILED,
            Map.of("amount", "1000.00", "currency", "INR", "failureReason", "INSUFFICIENT_FUNDS"),
            null);

    assertThat(email.subject()).isEqualTo("Transfer not completed");
    assertThat(email.text())
        .contains("not completed (insufficient funds). No money left your account.");
    assertThat(NotificationTemplates.render(NotificationKind.TRANSFER_FAILED, MONEY, null).text())
        .contains("(it could not be processed)");
  }

  @Test
  @DisplayName("Every email ends with the phishing warning")
  void footer() {
    assertThat(NotificationTemplates.render(NotificationKind.MONEY_RECEIVED, MONEY, null).text())
        .contains("We will never ask for your password");
  }
}
