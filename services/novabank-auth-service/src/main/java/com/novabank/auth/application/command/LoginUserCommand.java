package com.novabank.auth.application.command;

public record LoginUserCommand(

    String email,
    String password

) {
}
