package com.novabank.auth.presentation.rest.admin.response;

import com.novabank.auth.domain.model.RoleName;
import com.novabank.auth.domain.model.UserStatus;
import java.util.List;
import java.util.UUID;

/** Roles sorted, so responses are stable. */
public record StaffUserApiResponse(
    UUID userId, String email, UserStatus status, List<RoleName> roles) {}
