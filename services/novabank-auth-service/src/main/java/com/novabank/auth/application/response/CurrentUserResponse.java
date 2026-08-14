package com.novabank.auth.application.response;

import com.novabank.auth.domain.model.RoleName;
import java.util.Set;
import java.util.UUID;

public record CurrentUserResponse(

    UUID userId,

    String email,

    Set<RoleName> roles

) {

}
