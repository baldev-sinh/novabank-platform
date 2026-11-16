package com.novabank.payment.application.exception;

/** Also used for other users' payments, so their existence isn't revealed (404). */
public class PaymentNotFoundException extends RuntimeException {

  public PaymentNotFoundException() {
    super("Payment not found.");
  }
}
