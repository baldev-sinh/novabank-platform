package com.novabank.auth.presentation.rest.exception.response;

public record ValidationError(

    String field,

    String message

) {
}
