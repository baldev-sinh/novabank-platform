package com.novabank.customer.domain.valueobject.identifier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class UserIdTest {

  @Test
  @DisplayName("Should create user id")
  void shouldCreateUserId() {

    UUID value = UUID.randomUUID();

    UserId userId = new UserId(value);

    assertThat(userId.value()).isEqualTo(value);
  }

  @Test
  @DisplayName("Should reject null value")
  void shouldRejectNullValue() {

    assertThatThrownBy(() -> new UserId(null))
        .isInstanceOf(NullPointerException.class)
        .hasMessage("UserId cannot be null");
  }
}
