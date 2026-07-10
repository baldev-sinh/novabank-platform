package com.novabank.auth.presentation.rest.exception.response;

import java.time.Instant;

public record ApiError(

    Instant timestamp,

    int status,

    String error,

    String message,

    String path

) {
}
