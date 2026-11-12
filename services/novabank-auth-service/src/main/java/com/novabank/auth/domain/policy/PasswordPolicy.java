package com.novabank.auth.domain.policy;

import com.novabank.auth.domain.exception.DomainException;
import java.nio.charset.StandardCharsets;

/**
 * Rules for new passwords, following NIST SP 800-63B-4: at least 15 characters, up to at least 64
 * allowed, and no composition rules (no "must contain a digit/symbol").
 *
 * <p>BCrypt only accepts up to 72 bytes, so a password with many multi-byte characters can be
 * rejected even when it is under 64 characters.
 */
public final class PasswordPolicy {

  public static final int MIN_LENGTH = 15;
  public static final int MAX_LENGTH = 64;
  public static final int MAX_BYTES = 72;

  private PasswordPolicy() {}

  public static void check(String rawPassword) {
    if (rawPassword == null || rawPassword.isBlank()) {
      throw new DomainException("Password is required.");
    }
    int length = rawPassword.codePointCount(0, rawPassword.length());
    if (length < MIN_LENGTH) {
      throw new DomainException("Password must be at least " + MIN_LENGTH + " characters.");
    }
    if (length > MAX_LENGTH) {
      throw new DomainException("Password must be at most " + MAX_LENGTH + " characters.");
    }
    if (rawPassword.getBytes(StandardCharsets.UTF_8).length > MAX_BYTES) {
      throw new DomainException(
          "Password is too long: use fewer emoji or other special characters.");
    }
  }
}
