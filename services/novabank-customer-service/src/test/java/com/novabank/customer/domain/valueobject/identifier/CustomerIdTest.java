package com.novabank.customer.domain.valueobject.identifier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class CustomerIdTest {

  @Test
  @DisplayName("Should create customer id")
  void shouldCreateCustomerId() {

    UUID value = UUID.randomUUID();

    CustomerId customerId = new CustomerId(value);

    assertThat(customerId.value()).isEqualTo(value);
  }

  @Test
  @DisplayName("Should reject null value")
  void shouldRejectNullValue() {

    assertThatThrownBy(() -> new CustomerId(null))
        .isInstanceOf(NullPointerException.class)
        .hasMessage("CustomerId cannot be null");
  }

  @Test
  @DisplayName("Should generate random customer id")
  void shouldGenerateRandomCustomerId() {

    CustomerId customerId = CustomerId.random();

    assertThat(customerId).isNotNull();
    assertThat(customerId.value()).isNotNull();
  }
}
