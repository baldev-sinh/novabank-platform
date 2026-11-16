package com.novabank.account.domain.valueobject;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.novabank.account.domain.exception.DomainException;
import java.util.HashSet;
import java.util.Random;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class AccountNumberTest {

  @Test
  @DisplayName("Generated numbers are 12 digits, pass the MOD 97-10 check, and vary")
  void generatesValidNumbers() {
    Random random = new Random(42);
    Set<String> seen = new HashSet<>();
    for (int i = 0; i < 10_000; i++) {
      AccountNumber number = AccountNumber.generate(random);
      assertThat(number.value()).hasSize(12).doesNotStartWith("0");
      assertThat(Long.parseLong(number.value()) % 97).isEqualTo(1);
      seen.add(number.value());
    }
    assertThat(seen).hasSizeGreaterThan(9_990);
  }

  @Test
  @DisplayName("Every single-digit typo is rejected")
  void catchesSingleDigitTypos() {
    String valid = AccountNumber.generate(new Random(7)).value();
    for (int position = 0; position < valid.length(); position++) {
      for (char digit = '0'; digit <= '9'; digit++) {
        if (digit == valid.charAt(position)) {
          continue;
        }
        String typo = valid.substring(0, position) + digit + valid.substring(position + 1);
        assertThatThrownBy(() -> new AccountNumber(typo)).isInstanceOf(DomainException.class);
      }
    }
  }

  @Test
  @DisplayName("Swapping two different neighbouring digits is rejected")
  void catchesAdjacentSwaps() {
    String valid = AccountNumber.generate(new Random(11)).value();
    for (int i = 0; i < valid.length() - 1; i++) {
      if (valid.charAt(i) == valid.charAt(i + 1)) {
        continue;
      }
      char[] digits = valid.toCharArray();
      digits[i] = valid.charAt(i + 1);
      digits[i + 1] = valid.charAt(i);
      String swapped = new String(digits);
      assertThatThrownBy(() -> new AccountNumber(swapped)).isInstanceOf(DomainException.class);
    }
  }

  @Test
  @DisplayName("Separators are ignored; wrong length or letters are rejected")
  void parsing() {
    String valid = AccountNumber.generate(new Random(3)).value();
    String spaced = valid.substring(0, 4) + " " + valid.substring(4, 8) + "-" + valid.substring(8);

    assertThat(AccountNumber.of(spaced).value()).isEqualTo(valid);
    assertThatThrownBy(() -> AccountNumber.of("12345")).isInstanceOf(DomainException.class);
    assertThatThrownBy(() -> AccountNumber.of("12345678901A")).isInstanceOf(DomainException.class);
  }
}
