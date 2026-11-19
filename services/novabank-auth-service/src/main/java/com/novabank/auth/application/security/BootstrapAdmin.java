package com.novabank.auth.application.security;

import com.novabank.auth.domain.model.RoleName;
import com.novabank.auth.domain.model.User;
import com.novabank.auth.domain.valueobject.EmailAddress;
import java.util.Optional;

/**
 * The first administrator (2.10.1b). Granting ADMIN needs an ADMIN, so one email address from
 * configuration gets the role once it belongs to an active (verified) user: at startup if that user
 * already exists, or when they verify their email.
 *
 * <p>Unset it once the first administrator exists; from then on admins are managed through the API.
 */
public final class BootstrapAdmin {

  private final Optional<EmailAddress> email;

  private BootstrapAdmin(Optional<EmailAddress> email) {
    this.email = email;
  }

  public static BootstrapAdmin none() {
    return new BootstrapAdmin(Optional.empty());
  }

  public static BootstrapAdmin of(String email) {
    return email == null || email.isBlank()
        ? none()
        : new BootstrapAdmin(Optional.of(EmailAddress.of(email)));
  }

  public Optional<EmailAddress> email() {
    return email;
  }

  /**
   * Grants ADMIN if this is the configured, active user and they don't have it yet.
   *
   * @return true if the user was changed and must be saved
   */
  public boolean applyTo(User user) {
    if (email.isEmpty()
        || !email.get().equals(user.email())
        || !user.canSignIn()
        || user.roles().contains(RoleName.ADMIN)) {
      return false;
    }
    user.assignRole(RoleName.ADMIN);
    return true;
  }
}
