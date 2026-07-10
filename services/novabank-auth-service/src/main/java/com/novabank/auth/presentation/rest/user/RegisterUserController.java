package com.novabank.auth.presentation.rest.user;

import com.novabank.auth.application.command.RegisterUserCommand;
import com.novabank.auth.application.response.RegisterUserResponse;
import com.novabank.auth.application.usecase.RegisterUserUseCase;
import com.novabank.auth.presentation.rest.user.request.RegisterUserRequest;
import com.novabank.auth.presentation.rest.user.response.RegisterUserApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class RegisterUserController {

    private final RegisterUserUseCase registerUserUseCase;

    @PostMapping(
        value = "/register",
        consumes = MediaType.APPLICATION_JSON_VALUE,
        produces = MediaType.APPLICATION_JSON_VALUE
    )
    @ResponseStatus(HttpStatus.CREATED)
    public RegisterUserApiResponse register(
        @Valid
        @RequestBody
        RegisterUserRequest request) {

        RegisterUserResponse response =
            registerUserUseCase.register(
                new RegisterUserCommand(
                    request.email(),
                    request.password()));

        return new RegisterUserApiResponse(
            response.userId(),
            response.email(),
            response.status());
    }

}
