package com.novabank.auth.application.port.security;

import com.novabank.auth.application.security.AuthenticatedUser;

/** Issues access tokens. Validating them is the resource server's job, not the application's. */
public interface TokenService {

  String generateAccessToken(AuthenticatedUser user);

  /** Lifetime of a newly issued access token, in seconds. */
  long accessTokenExpiration();
}
