package com.novabank.auth.application.exception;

import com.novabank.auth.domain.model.RoleName;

/** CUSTOMER comes with registration; only staff roles are granted and revoked by an admin. */
public class RoleNotManageableException extends RuntimeException {

  public RoleNotManageableException(RoleName role) {
    super("Role " + role + " can't be granted or revoked by an administrator.");
  }
}
