package com.novabank.auth.infrastructure.security;

import com.novabank.auth.application.port.security.PasswordEncoder;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class BcryptPasswordEncoderAdapter implements PasswordEncoder {

  /**
   * BCrypt hash (cost 10, same as the real encoder) of a random value that was thrown away, so no
   * password can ever match it.
   */
  private static final String DUMMY_HASH =
      "$2a$10$yhjkvDQLAQTS9ymYWnwjP.sL6CxMrvs9ZKgvjyLhDvOtU83fpK2KO";

  private final org.springframework.security.crypto.password.PasswordEncoder delegate;

  @Override
  public String encode(String rawPassword) {
    return delegate.encode(rawPassword);
  }

  @Override
  public boolean matches(String rawPassword, String encodedPassword) {
    return delegate.matches(rawPassword, encodedPassword);
  }

  @Override
  public void simulateMatch(String rawPassword) {
    delegate.matches(rawPassword, DUMMY_HASH);
  }
}
