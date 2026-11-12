package com.novabank.auth.application.port.security;

public interface PasswordEncoder {

  String encode(String rawPassword);

  boolean matches(String rawPassword, String encodedPassword);

  /**
   * Does the same amount of work as {@link #matches} against a real hash and always fails. Call it
   * when no user exists, so the response time doesn't reveal whether an email is registered.
   */
  void simulateMatch(String rawPassword);
}
