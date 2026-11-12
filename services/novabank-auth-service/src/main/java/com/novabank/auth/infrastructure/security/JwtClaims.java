package com.novabank.auth.infrastructure.security;

/** Names of the custom claims in NovaBank access tokens. */
public final class JwtClaims {

  public static final String EMAIL = "email";
  public static final String ROLES = "roles";

  private JwtClaims() {}
}
