package com.novabank.auth.application.port.notification;

public interface VerificationEmailSender {

  /** Sends the verification link for this token to the address. */
  void send(String email, String token);
}
