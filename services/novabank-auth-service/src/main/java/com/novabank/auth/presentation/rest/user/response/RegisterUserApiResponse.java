package com.novabank.auth.presentation.rest.user.response;

import java.util.UUID;

public record RegisterUserApiResponse(

    UUID userId,

    String email,

    String status

) {
}
