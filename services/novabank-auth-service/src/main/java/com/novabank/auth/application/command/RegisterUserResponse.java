package com.novabank.auth.application.command;

public record RegisterUserResponse(

    String email,
    String password

) { }
