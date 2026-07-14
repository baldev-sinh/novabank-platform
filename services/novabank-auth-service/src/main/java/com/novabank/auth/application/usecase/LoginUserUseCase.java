package com.novabank.auth.application.usecase;

import com.novabank.auth.application.command.LoginUserCommand;
import com.novabank.auth.application.response.LoginUserResponse;

public interface LoginUserUseCase {

    LoginUserResponse login(LoginUserCommand command);

}
