package com.novabank.customer.domain.valueobject;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.novabank.customer.domain.exception.DomainException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class PhoneNumberTest {

  @ParameterizedTest
  @ValueSource(
      strings = {"+919876543210", "+91 98765 43210", "+1 (415) 555-0100", "+44.20.7946.0958"})
  @DisplayName("Accepts international numbers and strips separators")
  void acceptsAndNormalises(String input) {
    assertThat(PhoneNumber.of(input).value()).matches("\\+[1-9]\\d{1,14}");
  }

  @Test
  @DisplayName("Normalises to digits after the plus sign")
  void normalises() {
    assertThat(PhoneNumber.of(" +91 98765-43210 ").value()).isEqualTo("+919876543210");
  }

  @ParameterizedTest
  @ValueSource(
      strings = {"9876543210", "+0123456789", "+1234567890123456", "+91abc", "", "+", "   "})
  @DisplayName("Rejects numbers that aren't E.164")
  void rejectsInvalid(String input) {
    assertThatThrownBy(() -> PhoneNumber.of(input)).isInstanceOf(DomainException.class);
  }

  @Test
  @DisplayName("Rejects null")
  void rejectsNull() {
    assertThatThrownBy(() -> PhoneNumber.of(null)).isInstanceOf(NullPointerException.class);
  }
}
