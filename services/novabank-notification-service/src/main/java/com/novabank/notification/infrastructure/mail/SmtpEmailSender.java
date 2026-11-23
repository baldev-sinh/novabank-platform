package com.novabank.notification.infrastructure.mail;

import com.novabank.notification.application.port.EmailSender;
import com.novabank.notification.domain.EmailMessage;
import com.novabank.notification.infrastructure.configuration.NotificationProperties;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

/** SMTP via Spring's {@link JavaMailSender}; timeouts are set in {@code spring.mail.properties}. */
@Component
public class SmtpEmailSender implements EmailSender {

  private final JavaMailSender mailSender;
  private final NotificationProperties properties;

  public SmtpEmailSender(JavaMailSender mailSender, NotificationProperties properties) {
    this.mailSender = mailSender;
    this.properties = properties;
  }

  @Override
  public void send(String to, EmailMessage email) {
    SimpleMailMessage message = new SimpleMailMessage();
    message.setFrom(properties.from());
    message.setTo(to);
    message.setSubject(email.subject());
    message.setText(email.text());
    try {
      mailSender.send(message);
    } catch (MailException ex) {
      // The exception type only: its message can contain the address.
      throw new EmailDeliveryException(
          "Mail server refused or unreachable: " + ex.getClass().getSimpleName(), ex);
    }
  }
}
