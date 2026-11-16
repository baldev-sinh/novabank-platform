package com.novabank.auth.application.usecase;

public interface VerifyEmailUseCase {

  /**
   * Activates the account the token was issued for.
   *
   * @throws com.novabank.auth.application.exception.InvalidVerificationTokenException if the token
   *     is unknown, used or expired
   */
  void verify(String token);
}
