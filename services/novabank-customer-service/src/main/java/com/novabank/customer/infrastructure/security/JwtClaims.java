package com.novabank.customer.infrastructure.security;

/** Custom claim names in NovaBank access tokens (issued by the auth service). */
public final class JwtClaims {

  public static final String ROLES = "roles";

  private JwtClaims() {}
}
