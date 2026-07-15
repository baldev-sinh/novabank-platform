package com.novabank.auth.application.security;

import com.novabank.auth.domain.model.RoleName;
import java.util.Set;
import java.util.UUID;

public record JwtUser(

    UUID userId,

    String email,

    Set<RoleName> roles

) {

    public JwtUser {
        roles = Set.copyOf(roles);
    }
}
