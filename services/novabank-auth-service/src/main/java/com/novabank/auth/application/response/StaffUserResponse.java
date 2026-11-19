package com.novabank.auth.application.response;

import com.novabank.auth.domain.model.RoleName;
import com.novabank.auth.domain.model.UserStatus;
import java.util.Set;
import java.util.UUID;

public record StaffUserResponse(UUID userId, String email, UserStatus status, Set<RoleName> roles) {

  public StaffUserResponse {
    roles = Set.copyOf(roles);
  }
}
