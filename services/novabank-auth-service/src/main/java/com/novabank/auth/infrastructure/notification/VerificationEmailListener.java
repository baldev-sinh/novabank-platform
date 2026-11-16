package com.novabank.auth.infrastructure.notification;

import com.novabank.auth.application.port.notification.VerificationEmailSender;
import com.novabank.auth.application.verification.VerificationEmailRequested;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.MailException;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Sends the email only after the token is committed (no email for a rolled-back registration), and
 * on another thread, so a slow mail server doesn't slow down the request or reveal through timing
 * whether an address is registered. A lost email is recovered by the user asking for a resend.
 */
@Component
@RequiredArgsConstructor
public class VerificationEmailListener {

  private static final Logger LOGGER = LoggerFactory.getLogger(VerificationEmailListener.class);

  private final VerificationEmailSender sender;

  @Async
  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void on(VerificationEmailRequested request) {
    try {
      sender.send(request.email(), request.token());
      LOGGER.info("Verification email sent for user {}", request.userId());
    } catch (MailException ex) {
      // Exception type only: mail exception messages can contain the recipient address.
      LOGGER.warn(
          "Verification email for user {} could not be sent: {}",
          request.userId(),
          ex.getClass().getName());
    }
  }
}
