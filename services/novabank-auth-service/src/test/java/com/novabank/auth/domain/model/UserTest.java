package com.novabank.auth.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.novabank.auth.domain.exception.DomainException;
import com.novabank.auth.domain.valueobject.EmailAddress;
import com.novabank.auth.domain.valueobject.PasswordHash;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class UserTest {

    private static final EmailAddress EMAIL =
        EmailAddress.of("baldev@example.com");

    private static final PasswordHash PASSWORD =
        PasswordHash.of("$2a$10$abcdefghijklmnopqrstuv");

    @Test
    @DisplayName("Should register a new user")
    void shouldRegisterUser() {

        User user = User.register(EMAIL, PASSWORD);

        assertThat(user.id()).isNotNull();
        assertThat(user.email()).isEqualTo(EMAIL);
        assertThat(user.passwordHash()).isEqualTo(PASSWORD);
        assertThat(user.status()).isEqualTo(UserStatus.PENDING_VERIFICATION);
        assertThat(user.roles()).containsExactly(RoleName.CUSTOMER);
        assertThat(user.createdAt()).isNotNull();
        assertThat(user.updatedAt()).isNotNull();
    }

    @Test
    @DisplayName("Should activate a pending verification user")
    void shouldActivatePendingVerificationUser() {

        User user = User.register(EMAIL, PASSWORD);

        user.activate();

        assertThat(user.status()).isEqualTo(UserStatus.ACTIVE);
    }

    @Test
    @DisplayName("Should reject activation when user is already active")
    void shouldRejectActivationWhenAlreadyActive() {

        User user = User.register(EMAIL, PASSWORD);
        user.activate();

        assertThatThrownBy(user::activate)
            .isInstanceOf(DomainException.class)
            .hasMessage("Only users pending verification can be activated.");
    }

    @Test
    @DisplayName("Should lock an active user")
    void shouldLockActiveUser() {

        User user = User.register(EMAIL, PASSWORD);
        user.activate();

        user.lock();

        assertThat(user.status()).isEqualTo(UserStatus.LOCKED);
    }

    @Test
    @DisplayName("Should unlock a locked user")
    void shouldUnlockLockedUser() {

        User user = User.register(EMAIL, PASSWORD);
        user.activate();
        user.lock();

        user.unlock();

        assertThat(user.status()).isEqualTo(UserStatus.ACTIVE);
    }

    @Test
    @DisplayName("Should disable a user")
    void shouldDisableUser() {

        User user = User.register(EMAIL, PASSWORD);

        user.disable();

        assertThat(user.status()).isEqualTo(UserStatus.DISABLED);
    }

    @Test
    @DisplayName("Should change password")
    void shouldChangePassword() {

        User user = User.register(EMAIL, PASSWORD);

        PasswordHash newPassword =
            PasswordHash.of("$2a$10$mnopqrstuvwxyzabcdef");

        user.changePassword(newPassword);

        assertThat(user.passwordHash()).isEqualTo(newPassword);
    }

    @Test
    @DisplayName("Should reject changing to the same password")
    void shouldRejectChangingToSamePassword() {

        User user = User.register(EMAIL, PASSWORD);

        assertThatThrownBy(() -> user.changePassword(PASSWORD))
            .isInstanceOf(DomainException.class)
            .hasMessage("New password must be different from the current password.");
    }

    @Test
    @DisplayName("Should assign a new role")
    void shouldAssignNewRole() {

        User user = User.register(EMAIL, PASSWORD);

        user.assignRole(RoleName.ADMIN);

        assertThat(user.roles())
            .containsExactlyInAnyOrder(
                RoleName.CUSTOMER,
                RoleName.ADMIN
            );
    }

    @Test
    @DisplayName("Should reject duplicate role assignment")
    void shouldRejectDuplicateRoleAssignment() {

        User user = User.register(EMAIL, PASSWORD);

        assertThatThrownBy(() -> user.assignRole(RoleName.CUSTOMER))
            .isInstanceOf(DomainException.class)
            .hasMessage("User already has role CUSTOMER.");
    }

    @Test
    @DisplayName("Should remove an existing role")
    void shouldRemoveExistingRole() {

        User user = User.register(EMAIL, PASSWORD);
        user.assignRole(RoleName.ADMIN);

        user.removeRole(RoleName.ADMIN);

        assertThat(user.roles())
            .containsExactly(RoleName.CUSTOMER);
    }

    @Test
    @DisplayName("Should reject removing the last assigned role")
    void shouldRejectRemovingLastAssignedRole() {

        User user = User.register(EMAIL, PASSWORD);

        assertThatThrownBy(() -> user.removeRole(RoleName.CUSTOMER))
            .isInstanceOf(DomainException.class)
            .hasMessage("User must have at least one assigned role.");
    }

    @Test
    @DisplayName("Should return defensive copy of roles")
    void shouldReturnDefensiveCopyOfRoles() {

        User user = User.register(EMAIL, PASSWORD);

        var roles = user.roles();

        assertThatThrownBy(roles::clear)
            .isInstanceOf(UnsupportedOperationException.class);

        assertThat(user.roles())
            .containsExactly(RoleName.CUSTOMER);
    }

    @Test
    @DisplayName("Should support identity equality")
    void shouldSupportIdentityEquality() {

        User user = User.register(EMAIL, PASSWORD);

        assertThat(user)
            .isEqualTo(user)
            .hasSameHashCodeAs(user);
    }

    @Test
    @DisplayName("Should not expose password hash in string representation")
    void shouldNotExposePasswordHashInToString() {

        User user = User.register(EMAIL, PASSWORD);

        assertThat(user.toString())
            .doesNotContain("passwordHash");
    }
}
