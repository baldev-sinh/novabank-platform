package com.novabank.auth.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.novabank.auth.application.exception.RoleNotManageableException;
import com.novabank.auth.application.exception.StaffRoleChangeNotAllowedException;
import com.novabank.auth.application.exception.UserNotFoundException;
import com.novabank.auth.application.port.security.CurrentUserProvider;
import com.novabank.auth.application.security.AuthenticatedUser;
import com.novabank.auth.domain.model.RoleName;
import com.novabank.auth.domain.model.User;
import com.novabank.auth.domain.model.UserStatus;
import com.novabank.auth.domain.repository.UserRepository;
import com.novabank.auth.domain.valueobject.EmailAddress;
import com.novabank.auth.domain.valueobject.PasswordHash;
import com.novabank.auth.domain.valueobject.identifier.UserId;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
// The admin stub isn't needed by every test (some fail before reading the caller).
@MockitoSettings(strictness = Strictness.LENIENT)
class StaffAdministrationServiceTest {

  private static final PasswordHash HASH =
      PasswordHash.of("$2a$10$yhjkvDQLAQTS9ymYWnwjP.sL6CxMrvs9ZKgvjyLhDvOtU83fpK2KO");
  private static final UUID ADMIN_ID = UUID.randomUUID();

  @Mock private UserRepository users;
  @Mock private CurrentUserProvider currentUser;

  private StaffAdministrationService service;

  @BeforeEach
  void setUp() {
    service = new StaffAdministrationService(users, currentUser);
    when(currentUser.getCurrentUser())
        .thenReturn(new AuthenticatedUser(ADMIN_ID, "admin@example.com", Set.of(RoleName.ADMIN)));
  }

  @Test
  @DisplayName("Granting a staff role to an active user adds it and saves the user")
  void grantAddsRole() {
    User user = stored(activeUser());

    service.grantRole(user.id().value(), RoleName.OPERATIONS);

    assertThat(user.roles()).contains(RoleName.CUSTOMER, RoleName.OPERATIONS);
    verify(users).save(user);
  }

  @Test
  @DisplayName("Granting a role the user already has changes nothing")
  void grantIsIdempotent() {
    User user = activeUser();
    user.assignRole(RoleName.OPERATIONS);
    stored(user);

    service.grantRole(user.id().value(), RoleName.OPERATIONS);

    verify(users, never()).save(any());
  }

  @Test
  @DisplayName("Roles can't be granted to a user who hasn't verified their email")
  void grantRequiresActiveUser() {
    User user = stored(User.register(EmailAddress.of("pending@example.com"), HASH));

    assertThatThrownBy(() -> service.grantRole(user.id().value(), RoleName.OPERATIONS))
        .isInstanceOf(StaffRoleChangeNotAllowedException.class)
        .hasMessageContaining(UserStatus.PENDING_VERIFICATION.name());
    verify(users, never()).save(any());
  }

  @Test
  @DisplayName("CUSTOMER is never granted or revoked by an administrator")
  void customerRoleIsNotManageable() {
    UUID anyone = UUID.randomUUID();

    assertThatThrownBy(() -> service.grantRole(anyone, RoleName.CUSTOMER))
        .isInstanceOf(RoleNotManageableException.class);
    assertThatThrownBy(() -> service.revokeRole(anyone, RoleName.CUSTOMER))
        .isInstanceOf(RoleNotManageableException.class);
  }

  @Test
  @DisplayName("An unknown user id is a 'not found'")
  void unknownUser() {
    UUID unknown = UUID.randomUUID();
    when(users.findById(UserId.of(unknown))).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.grantRole(unknown, RoleName.OPERATIONS))
        .isInstanceOf(UserNotFoundException.class);
  }

  @Test
  @DisplayName("Revoking removes the role; revoking one the user doesn't have changes nothing")
  void revokeRemovesRoleAndIsIdempotent() {
    User user = activeUser();
    user.assignRole(RoleName.COMPLIANCE_OFFICER);
    stored(user);

    service.revokeRole(user.id().value(), RoleName.COMPLIANCE_OFFICER);
    service.revokeRole(user.id().value(), RoleName.COMPLIANCE_OFFICER);

    assertThat(user.roles()).containsExactly(RoleName.CUSTOMER);
    verify(users).save(user);
  }

  @Test
  @DisplayName("An administrator can't revoke their own ADMIN")
  void cannotRevokeOwnAdmin() {
    assertThatThrownBy(() -> service.revokeRole(ADMIN_ID, RoleName.ADMIN))
        .isInstanceOf(StaffRoleChangeNotAllowedException.class);
    verify(users, never()).save(any());
  }

  @Test
  @DisplayName("Looking up an unknown email is a 'not found'")
  void findUnknownEmail() {
    when(users.findByEmail(EmailAddress.of("nobody@example.com"))).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.findByEmail("nobody@example.com"))
        .isInstanceOf(UserNotFoundException.class);
  }

  private static User activeUser() {
    User user = User.register(EmailAddress.of("staff-" + UUID.randomUUID() + "@example.com"), HASH);
    user.activate();
    return user;
  }

  private User stored(User user) {
    when(users.findById(user.id())).thenReturn(Optional.of(user));
    return user;
  }
}
