package com.novabank.platform.money;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.Currency;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class MoneyTest {

  @Test
  @DisplayName("Amounts get exactly the currency's ISO 4217 decimals")
  void normalisesScale() {
    assertThat(Money.of("10.5", "INR").amountString()).isEqualTo("10.50");
    assertThat(Money.of("100", "JPY").amountString()).isEqualTo("100");
    assertThat(Money.of("1.5", "KWD").amountString()).isEqualTo("1.500");
    assertThat(Money.of("10.500", "INR").amountString()).isEqualTo("10.50");
  }

  @Test
  @DisplayName("Too many decimals are rejected, never rounded")
  void rejectsExtraDecimals() {
    assertThatThrownBy(() -> Money.of("10.555", "INR"))
        .isInstanceOf(InvalidMoneyException.class)
        .hasMessageContaining("at most 2 decimal places");
    assertThatThrownBy(() -> Money.of("1.5", "JPY")).isInstanceOf(InvalidMoneyException.class);
  }

  @Test
  @DisplayName("Bad numbers, unknown and pseudo currencies are rejected")
  void rejectsBadInput() {
    assertThatThrownBy(() -> Money.of("ten", "INR")).isInstanceOf(InvalidMoneyException.class);
    assertThatThrownBy(() -> Money.of("10", "ABC")).isInstanceOf(InvalidMoneyException.class);
    assertThatThrownBy(() -> Money.of("10", "XAU")).isInstanceOf(InvalidMoneyException.class);
  }

  @Test
  @DisplayName("Arithmetic is exact and equal values compare equal regardless of input scale")
  void arithmeticAndEquality() {
    Money a = Money.of("0.10", "INR");
    Money b = Money.of("0.2", "INR");

    assertThat(a.plus(b)).isEqualTo(Money.of("0.30", "INR"));
    assertThat(a.minus(b)).isEqualTo(Money.of("-0.10", "INR"));
    assertThat(a.minus(b).isNegative()).isTrue();
    assertThat(a.negate().negate()).isEqualTo(a);
    assertThat(Money.zero(Currency.getInstance("INR")).isZero()).isTrue();
    assertThat(new Money(new BigDecimal("5"), Currency.getInstance("INR")))
        .isEqualTo(Money.of("5.00", "INR"));
    assertThat(a.compareTo(b)).isNegative();
  }

  @Test
  @DisplayName("Different currencies are never mixed")
  void rejectsCurrencyMismatch() {
    assertThatThrownBy(() -> Money.of("1", "INR").plus(Money.of("1", "EUR")))
        .isInstanceOf(InvalidMoneyException.class)
        .hasMessageContaining("INR vs EUR");
  }

  @Test
  @DisplayName("Large amounts never use scientific notation")
  void plainString() {
    assertThat(Money.of("10000000000", "INR").amountString()).isEqualTo("10000000000.00");
    assertThat(Money.of("1E+3", "INR").toString()).isEqualTo("1000.00 INR");
  }
}
