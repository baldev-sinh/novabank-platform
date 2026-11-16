package com.novabank.auth.infrastructure.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

import com.novabank.auth.application.port.notification.VerificationEmailSender;
import com.novabank.auth.application.verification.VerificationEmailRequested;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

@ExtendWith(MockitoExtension.class)
class VerificationEmailNotificationTest {

  @Mock private JavaMailSender mailSender;
  @Mock private VerificationEmailSender sender;

  @Test
  @DisplayName("The email contains the link built from the template")
  void buildsEmail() {
    EmailVerificationProperties properties =
        new EmailVerificationProperties(
            Duration.ofHours(24),
            Duration.ofSeconds(60),
            "no-reply@novabank.example",
            "https://app.example/verify-email#token={token}");

    new SmtpVerificationEmailSender(mailSender, properties).send("user@example.com", "abc_123");

    ArgumentCaptor<SimpleMailMessage> message = ArgumentCaptor.forClass(SimpleMailMessage.class);
    verify(mailSender).send(message.capture());
    assertThat(message.getValue().getTo()).containsExactly("user@example.com");
    assertThat(message.getValue().getFrom()).isEqualTo("no-reply@novabank.example");
    assertThat(message.getValue().getText())
        .contains("https://app.example/verify-email#token=abc_123")
        .contains("24 hours");
  }

  @Test
  @DisplayName("A mail failure is logged, not thrown: the registration has already committed")
  void swallowsMailFailure() {
    doThrow(new MailSendException("down")).when(sender).send(any(), any());

    assertThatCode(() ->
            new VerificationEmailListener(sender)
                .on(new VerificationEmailRequested(UUID.randomUUID(), "u@x.io", "t")))
        .doesNotThrowAnyException();
  }
}
