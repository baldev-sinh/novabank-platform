package com.novabank.auth.application.usecase;

import com.novabank.auth.application.response.LoginUserResponse;

/** Keeps a sign-in alive with refresh tokens, and ends it (7.1). */
public interface SessionUseCase {

  /**
   * Exchanges a refresh token for a new access token and the next refresh token (rotation).
   *
   * @throws com.novabank.auth.application.exception.InvalidRefreshTokenException if the token can't
   *     be used; a token that was already used revokes the whole sign-in
   */
  LoginUserResponse refresh(String refreshToken);

  /** Ends the sign-in the token belongs to. Idempotent: unknown or ended tokens are ignored. */
  void logout(String refreshToken);
}
