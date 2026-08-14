package com.novabank.auth.application.response;

import java.util.UUID;

public record RegisterUserResponse(UUID userId, String email, String status) {}
