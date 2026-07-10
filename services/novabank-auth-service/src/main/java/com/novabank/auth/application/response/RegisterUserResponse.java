package com.novabank.auth.application.response;


import com.novabank.auth.domain.model.UserStatus;
import java.util.UUID;

public record RegisterUserResponse(

    UUID userId,

    String email,

    String status

) {}
