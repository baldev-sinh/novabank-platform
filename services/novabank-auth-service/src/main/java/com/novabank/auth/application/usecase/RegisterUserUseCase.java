package com.novabank.auth.application.usecase;

import com.novabank.auth.application.command.RegisterUserCommand;
import com.novabank.auth.application.response.RegisterUserResponse;

public interface RegisterUserUseCase {

    RegisterUserResponse register(RegisterUserCommand command);

}
