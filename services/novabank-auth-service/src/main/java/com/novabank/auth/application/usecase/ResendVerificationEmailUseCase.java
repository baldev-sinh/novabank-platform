package com.novabank.auth.application.usecase;

public interface ResendVerificationEmailUseCase {

  /**
   * Sends a new verification email if the address belongs to an unverified account. The result is
   * the same either way, so callers can't find out which emails are registered.
   */
  void resend(String email);
}
