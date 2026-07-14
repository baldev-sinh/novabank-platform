package com.novabank.auth.application.response;

import java.util.UUID;

public record LoginUserResponse(

    UUID userId,
    String email,
    String status

) {
}
