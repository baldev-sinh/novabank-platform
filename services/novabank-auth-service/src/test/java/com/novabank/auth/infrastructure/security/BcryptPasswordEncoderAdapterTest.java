package com.novabank.auth.infrastructure.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

class BcryptPasswordEncoderAdapterTest {

  private final BCryptPasswordEncoder bcrypt = spy(new BCryptPasswordEncoder());
  private final BcryptPasswordEncoderAdapter adapter = new BcryptPasswordEncoderAdapter(bcrypt);

  @Test
  @DisplayName("Encodes and matches passwords with BCrypt")
  void encodesAndMatches() {
    String hash = adapter.encode("Password@123");

    assertThat(hash).startsWith("$2a$10$");
    assertThat(adapter.matches("Password@123", hash)).isTrue();
    assertThat(adapter.matches("wrong", hash)).isFalse();
  }

  @Test
  @DisplayName("simulateMatch runs a real cost-10 BCrypt comparison")
  void simulateMatchRunsRealComparison() {
    adapter.simulateMatch("Password@123");

    verify(bcrypt).matches(eq("Password@123"), startsWith("$2a$10$"));
  }
}
