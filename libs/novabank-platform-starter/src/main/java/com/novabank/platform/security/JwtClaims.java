package com.novabank.platform.security;

/** Custom claim names in NovaBank access tokens (issued by the auth service). */
public final class JwtClaims {

  public static final String EMAIL = "email";
  public static final String ROLES = "roles";

  private JwtClaims() {}
}
