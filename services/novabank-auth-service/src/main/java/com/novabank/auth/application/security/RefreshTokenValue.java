package com.novabank.auth.application.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

/**
 * An opaque refresh token: 256 random bits, so SHA-256 is enough for storage (it can't be guessed
 * back from its hash). Only the hash is stored; the value lives in the user's HttpOnly cookie.
 */
public record RefreshTokenValue(String value, String hash) {

  private static final SecureRandom RANDOM = new SecureRandom();

  public static RefreshTokenValue generate() {
    byte[] bytes = new byte[32];
    RANDOM.nextBytes(bytes);
    String value = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    return new RefreshTokenValue(value, hashOf(value));
  }

  /** Lowercase hex SHA-256, as stored in the database. */
  public static String hashOf(String value) {
    try {
      byte[] digest =
          MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
      return HexFormat.of().formatHex(digest);
    } catch (NoSuchAlgorithmException ex) {
      throw new IllegalStateException("SHA-256 is required on every Java platform", ex);
    }
  }

  /** Never print the token itself. */
  @Override
  public String toString() {
    return "RefreshTokenValue[****]";
  }
}
