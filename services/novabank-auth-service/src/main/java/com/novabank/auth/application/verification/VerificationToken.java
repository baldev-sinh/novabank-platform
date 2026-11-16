package com.novabank.auth.application.verification;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

/**
 * A one-time token sent to the user. 256 random bits, so a fast hash (SHA-256) is enough for
 * storage: unlike a password, it can't be guessed or brute-forced back from its hash.
 */
public record VerificationToken(String value, String hash) {

  private static final SecureRandom RANDOM = new SecureRandom();

  public static VerificationToken generate() {
    byte[] bytes = new byte[32];
    RANDOM.nextBytes(bytes);
    String value = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    return new VerificationToken(value, hashOf(value));
  }

  /** Lowercase hex SHA-256 of the token, as stored in the database. */
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
    return "VerificationToken[****]";
  }
}
