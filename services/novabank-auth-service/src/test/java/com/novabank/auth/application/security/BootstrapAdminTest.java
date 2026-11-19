package com.novabank.auth.application.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.novabank.auth.domain.model.RoleName;
import com.novabank.auth.domain.model.User;
import com.novabank.auth.domain.valueobject.EmailAddress;
import com.novabank.auth.domain.valueobject.PasswordHash;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class BootstrapAdminTest {

  private static final PasswordHash HASH =
      PasswordHash.of("$2a$10$yhjkvDQLAQTS9ymYWnwjP.sL6CxMrvs9ZKgvjyLhDvOtU83fpK2KO");
  private static final String EMAIL = "first-admin@example.com";

  @Test
  @DisplayName("The configured, active user becomes ADMIN, once")
  void grantsAdminToConfiguredActiveUser() {
    User user = active(EMAIL);
    BootstrapAdmin bootstrap = BootstrapAdmin.of(EMAIL);

    assertThat(bootstrap.applyTo(user)).isTrue();
    assertThat(user.roles()).contains(RoleName.ADMIN);
    assertThat(bootstrap.applyTo(user)).isFalse();
  }

  @Test
  @DisplayName("Nothing happens for other users, unverified users, or when unset")
  void otherwiseChangesNothing() {
    User pending = User.register(EmailAddress.of(EMAIL), HASH);

    assertThat(BootstrapAdmin.of(EMAIL).applyTo(pending)).isFalse();
    assertThat(BootstrapAdmin.of(EMAIL).applyTo(active("someone-else@example.com"))).isFalse();
    assertThat(BootstrapAdmin.of("").applyTo(active(EMAIL))).isFalse();
    assertThat(BootstrapAdmin.none().applyTo(active(EMAIL))).isFalse();
    assertThat(pending.roles()).containsExactly(RoleName.CUSTOMER);
  }

  private static User active(String email) {
    User user = User.register(EmailAddress.of(email), HASH);
    user.activate();
    return user;
  }
}
