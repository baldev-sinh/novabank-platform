package com.novabank.notification.application.port;

import com.novabank.notification.domain.EmailMessage;

public interface EmailSender {

  /**
   * @throws EmailDeliveryException if the mail server didn't accept it
   */
  void send(String to, EmailMessage message);

  class EmailDeliveryException extends RuntimeException {
    public EmailDeliveryException(String message, Throwable cause) {
      super(message, cause);
    }
  }
}
