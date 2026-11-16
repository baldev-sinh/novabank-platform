package com.novabank.platform.money;

import java.math.BigDecimal;
import java.util.Currency;
import java.util.Objects;

/**
 * An exact amount in one currency. Plain Java (no framework), so domain models can use it.
 *
 * <p>The amount always has exactly the currency's ISO 4217 number of decimals (INR 2, JPY 0, KWD
 * 3): {@code 10.5 INR} becomes {@code 10.50}, while {@code 10.555 INR} is rejected rather than
 * silently rounded. Store it as {@code NUMERIC(19,4)} and send it as a decimal string plus currency
 * code, e.g. {@code {"amount":"10.50","currency":"INR"}}.
 */
public record Money(BigDecimal amount, Currency currency) implements Comparable<Money> {

  public Money {
    Objects.requireNonNull(amount, "amount cannot be null");
    Objects.requireNonNull(currency, "currency cannot be null");
    int digits = currency.getDefaultFractionDigits();
    if (digits < 0) {
      // Pseudo-currencies such as XAU (gold) have no defined decimals.
      throw new InvalidMoneyException("Currency " + currency + " is not supported.");
    }
    if (amount.stripTrailingZeros().scale() > digits) {
      throw new InvalidMoneyException(
          currency + " amounts can have at most " + digits + " decimal places.");
    }
    // Exact: the scale only grows here, so no rounding can happen.
    amount = amount.setScale(digits);
  }

  /**
   * @param amount a plain decimal string such as {@code "125.50"}
   * @param currencyCode an ISO 4217 code such as {@code "INR"}
   */
  public static Money of(String amount, String currencyCode) {
    Objects.requireNonNull(amount, "amount cannot be null");
    Objects.requireNonNull(currencyCode, "currencyCode cannot be null");
    try {
      return new Money(new BigDecimal(amount), Currency.getInstance(currencyCode));
    } catch (NumberFormatException ex) {
      throw new InvalidMoneyException("Amount must be a decimal number.");
    } catch (IllegalArgumentException ex) {
      if (ex instanceof InvalidMoneyException invalid) {
        throw invalid;
      }
      throw new InvalidMoneyException("Unknown currency code.");
    }
  }

  public static Money zero(Currency currency) {
    return new Money(BigDecimal.ZERO, currency);
  }

  public Money plus(Money other) {
    requireSameCurrency(other);
    return new Money(amount.add(other.amount), currency);
  }

  public Money minus(Money other) {
    requireSameCurrency(other);
    return new Money(amount.subtract(other.amount), currency);
  }

  public Money negate() {
    return new Money(amount.negate(), currency);
  }

  public boolean isPositive() {
    return amount.signum() > 0;
  }

  public boolean isNegative() {
    return amount.signum() < 0;
  }

  public boolean isZero() {
    return amount.signum() == 0;
  }

  public boolean isSameCurrency(Money other) {
    return currency.equals(other.currency);
  }

  /** The amount as a plain decimal string (never scientific notation), e.g. {@code "125.50"}. */
  public String amountString() {
    return amount.toPlainString();
  }

  public String currencyCode() {
    return currency.getCurrencyCode();
  }

  @Override
  public int compareTo(Money other) {
    requireSameCurrency(other);
    return amount.compareTo(other.amount);
  }

  @Override
  public String toString() {
    return amountString() + " " + currencyCode();
  }

  private void requireSameCurrency(Money other) {
    Objects.requireNonNull(other, "other cannot be null");
    if (!isSameCurrency(other)) {
      throw new InvalidMoneyException(
          "Currency mismatch: " + currencyCode() + " vs " + other.currencyCode());
    }
  }
}
