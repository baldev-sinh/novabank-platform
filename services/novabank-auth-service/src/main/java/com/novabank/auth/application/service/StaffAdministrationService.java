package com.novabank.auth.application.service;

import com.novabank.auth.application.event.StaffRoleChangedEvent;
import com.novabank.auth.application.exception.RoleNotManageableException;
import com.novabank.auth.application.exception.StaffRoleChangeNotAllowedException;
import com.novabank.auth.application.exception.UserNotFoundException;
import com.novabank.auth.application.port.messaging.EventOutbox;
import com.novabank.auth.application.port.security.CurrentUserProvider;
import com.novabank.auth.application.response.StaffUserResponse;
import com.novabank.auth.application.usecase.StaffAdministrationUseCase;
import com.novabank.auth.domain.model.RoleName;
import com.novabank.auth.domain.model.User;
import com.novabank.auth.domain.repository.UserRepository;
import com.novabank.auth.domain.valueobject.EmailAddress;
import com.novabank.auth.domain.valueobject.identifier.UserId;
import java.time.Clock;
import java.util.Objects;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Role changes take effect at the user's next sign-in: access tokens carry the roles and live 15
 * minutes. Every change is published as {@code StaffRoleGranted/Revoked.v1} in the same transaction
 * (outbox), so the audit service records it, and logged with both user ids (never emails).
 */
@Service
@RequiredArgsConstructor
public class StaffAdministrationService implements StaffAdministrationUseCase {

  private static final Logger LOGGER = LoggerFactory.getLogger(StaffAdministrationService.class);

  private final UserRepository users;
  private final CurrentUserProvider currentUser;
  private final EventOutbox eventOutbox;
  private final Clock clock;

  @Override
  @Transactional(readOnly = true)
  public StaffUserResponse findByEmail(String email) {
    Objects.requireNonNull(email, "email cannot be null");
    User user = users.findByEmail(EmailAddress.of(email)).orElseThrow(UserNotFoundException::new);
    return new StaffUserResponse(
        user.id().value(), user.email().value(), user.status(), user.roles());
  }

  @Override
  @Transactional
  public void grantRole(UUID userId, RoleName role) {
    requireStaffRole(role);
    User user = load(userId);
    if (user.roles().contains(role)) {
      return;
    }
    // A role on a pending, locked or disabled account would sit there unnoticed.
    if (!user.canSignIn()) {
      throw new StaffRoleChangeNotAllowedException(
          "Roles can only be granted to active users (this one is " + user.status() + ").");
    }
    user.assignRole(role);
    users.save(user);
    UUID actor = actorId();
    eventOutbox.add(StaffRoleChangedEvent.granted(userId, role, actor, clock.instant()));
    LOGGER.info("Role {} granted to user {} by admin {}", role, userId, actor);
  }

  @Override
  @Transactional
  public void revokeRole(UUID userId, RoleName role) {
    requireStaffRole(role);
    UUID actor = actorId();
    // Otherwise the last administrator could lock everyone out of administration.
    if (role == RoleName.ADMIN && actor.equals(userId)) {
      throw new StaffRoleChangeNotAllowedException("Administrators can't revoke their own ADMIN.");
    }
    User user = load(userId);
    if (!user.roles().contains(role)) {
      return;
    }
    user.removeRole(role);
    users.save(user);
    eventOutbox.add(StaffRoleChangedEvent.revoked(userId, role, actor, clock.instant()));
    LOGGER.info("Role {} revoked from user {} by admin {}", role, userId, actor);
  }

  private static void requireStaffRole(RoleName role) {
    Objects.requireNonNull(role, "role cannot be null");
    if (role == RoleName.CUSTOMER) {
      throw new RoleNotManageableException(role);
    }
  }

  private User load(UUID userId) {
    Objects.requireNonNull(userId, "userId cannot be null");
    return users.findById(UserId.of(userId)).orElseThrow(UserNotFoundException::new);
  }

  private UUID actorId() {
    return currentUser.getCurrentUser().userId();
  }
}
