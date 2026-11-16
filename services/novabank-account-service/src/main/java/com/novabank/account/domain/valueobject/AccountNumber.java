package com.novabank.account.domain.valueobject;

import com.novabank.account.domain.exception.DomainException;
import java.util.Objects;
import java.util.random.RandomGenerator;

/**
 * The customer-facing account number (ADR-004): 10 random digits plus 2 check digits computed with
 * ISO 7064 MOD 97-10, the scheme IBANs use. A valid number is {@code ≡ 1 (mod 97)}, which catches
 * every single-digit typo and almost every swap of two digits, before any money moves.
 *
 * <p>Deliberately not an IBAN: that would claim a real country and bank code.
 */
public record AccountNumber(String value) {

  public static final int LENGTH = 12;
  private static final long BASE_RANGE = 9_000_000_000L; // 10 digits, no leading zero

  public AccountNumber {
    Objects.requireNonNull(value, "Account number cannot be null");
    if (!value.matches("\\d{" + LENGTH + "}")) {
      throw new DomainException("Account number must be " + LENGTH + " digits.");
    }
    if (Long.parseLong(value) % 97 != 1) {
      throw new DomainException("Account number check digits are invalid.");
    }
  }

  /** Separators such as spaces or dashes are ignored, so {@code "1234 5678 9012"} works. */
  public static AccountNumber of(String raw) {
    Objects.requireNonNull(raw, "Account number cannot be null");
    return new AccountNumber(raw.replaceAll("[\\s-]", ""));
  }

  public static AccountNumber generate(RandomGenerator random) {
    long base = 1_000_000_000L + random.nextLong(BASE_RANGE);
    long check = 98 - (base * 100 % 97);
    return new AccountNumber(base + String.format("%02d", check));
  }

  @Override
  public String toString() {
    return value;
  }
}
