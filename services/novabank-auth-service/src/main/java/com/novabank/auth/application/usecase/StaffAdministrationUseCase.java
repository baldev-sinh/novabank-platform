package com.novabank.auth.application.usecase;

import com.novabank.auth.application.response.StaffUserResponse;
import com.novabank.auth.domain.model.RoleName;
import java.util.UUID;

/**
 * An administrator grants and revokes staff roles (2.10.1b). Staff register and verify their email
 * like anyone else, so they choose their own password; an administrator never handles one.
 */
public interface StaffAdministrationUseCase {

  /** Finds a user so their id can be used to change roles. */
  StaffUserResponse findByEmail(String email);

  /** Idempotent: granting a role the user already has changes nothing. */
  void grantRole(UUID userId, RoleName role);

  /** Idempotent: revoking a role the user doesn't have changes nothing. */
  void revokeRole(UUID userId, RoleName role);
}
