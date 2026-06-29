package com.novabank.auth.application.command;

public record RegisterUserCommand(

    String email,
    String password

) { }
