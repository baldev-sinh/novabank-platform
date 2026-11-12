package com.novabank.auth.domain.policy;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.novabank.auth.domain.exception.DomainException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class PasswordPolicyTest {

  @Test
  @DisplayName("Accepts a passphrase without any composition rules")
  void acceptsLongPassphrase() {
    assertThatCode(() -> PasswordPolicy.check("correct horse battery staple"))
        .doesNotThrowAnyException();
  }

  @Test
  @DisplayName("Accepts exactly the minimum and maximum length")
  void acceptsBoundaries() {
    assertThatCode(() -> PasswordPolicy.check("a".repeat(15))).doesNotThrowAnyException();
    assertThatCode(() -> PasswordPolicy.check("a".repeat(64))).doesNotThrowAnyException();
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {"   "})
  @DisplayName("Rejects a missing password")
  void rejectsMissing(String password) {
    assertThatThrownBy(() -> PasswordPolicy.check(password))
        .isInstanceOf(DomainException.class)
        .hasMessage("Password is required.");
  }

  @Test
  @DisplayName("Rejects passwords shorter than 15 characters")
  void rejectsTooShort() {
    assertThatThrownBy(() -> PasswordPolicy.check("Password@123"))
        .isInstanceOf(DomainException.class)
        .hasMessage("Password must be at least 15 characters.");
  }

  @Test
  @DisplayName("Rejects passwords longer than 64 characters")
  void rejectsTooLong() {
    assertThatThrownBy(() -> PasswordPolicy.check("a".repeat(65)))
        .isInstanceOf(DomainException.class)
        .hasMessage("Password must be at most 64 characters.");
  }

  @Test
  @DisplayName("Rejects passwords over BCrypt's 72-byte limit even under 64 characters")
  void rejectsOver72Bytes() {
    String twentyFiveEmoji = "😀".repeat(25); // 25 characters, 100 UTF-8 bytes

    assertThatThrownBy(() -> PasswordPolicy.check(twentyFiveEmoji))
        .isInstanceOf(DomainException.class)
        .hasMessageStartingWith("Password is too long");
  }
}
