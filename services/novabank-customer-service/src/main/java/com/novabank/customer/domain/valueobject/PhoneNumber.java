package com.novabank.customer.domain.valueobject;

import com.novabank.customer.domain.exception.DomainException;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * A phone number in E.164 format, e.g. {@code +919876543210}: a plus sign, a country code, and at
 * most 15 digits in total. Spaces, dashes, dots and parentheses are accepted on input and removed.
 */
public record PhoneNumber(String value) {

  private static final Pattern E164 = Pattern.compile("^\\+[1-9]\\d{1,14}$");
  private static final Pattern SEPARATORS = Pattern.compile("[\\s\\-().]");

  public PhoneNumber {
    Objects.requireNonNull(value, "Phone number cannot be null");
    value = SEPARATORS.matcher(value.trim()).replaceAll("");
    if (!E164.matcher(value).matches()) {
      throw new DomainException(
          "Phone number must be in international format, e.g. +919876543210.");
    }
  }

  public static PhoneNumber of(String value) {
    return new PhoneNumber(value);
  }

  @Override
  public String toString() {
    return value;
  }
}
