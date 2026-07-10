package com.novabank.auth.presentation.rest.exception.response;

import java.time.Instant;
import java.util.List;

public record ValidationApiError(

    Instant timestamp,

    int status,

    String error,

    List<ValidationError> errors,

    String path

) {
}
