package com.novabank.auth.infrastructure.notification;

import com.novabank.auth.application.port.notification.VerificationEmailSender;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class SmtpVerificationEmailSender implements VerificationEmailSender {

  static final String SUBJECT = "Verify your NovaBank email address";

  private final JavaMailSender mailSender;
  private final EmailVerificationProperties properties;

  @Override
  public void send(String email, String token) {
    // The token is base64url, so it's safe in a URL as is.
    String link = properties.linkTemplate().replace("{token}", token);

    SimpleMailMessage message = new SimpleMailMessage();
    message.setFrom(properties.from());
    message.setTo(email);
    message.setSubject(SUBJECT);
    message.setText(
        """
        Welcome to NovaBank.

        Please confirm your email address by opening this link:
        %s

        The link expires in %s. If you didn't create an account, you can ignore this email.
        """
            .formatted(link, describe(properties.tokenTtl())));

    mailSender.send(message);
  }

  private static String describe(Duration duration) {
    long hours = duration.toHours();
    return hours >= 1 ? hours + " hours" : duration.toMinutes() + " minutes";
  }
}
