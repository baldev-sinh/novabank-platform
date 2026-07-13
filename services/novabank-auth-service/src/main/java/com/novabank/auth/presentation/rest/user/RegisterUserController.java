package com.novabank.auth.presentation.rest.user;

import com.novabank.auth.application.command.RegisterUserCommand;
import com.novabank.auth.application.response.RegisterUserResponse;
import com.novabank.auth.application.usecase.RegisterUserUseCase;
import com.novabank.auth.presentation.rest.user.request.RegisterUserRequest;
import com.novabank.auth.presentation.rest.user.response.RegisterUserApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;


@Tag(name = "Users")
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
    @Operation(
        summary = "Register a new user",
        description = "Registers a new user account."
    )
    @ApiResponses({
        @ApiResponse(
            responseCode = "201",
            description = "User registered successfully"
        ),
        @ApiResponse(
            responseCode = "400",
            description = "Validation failed"
        ),
        @ApiResponse(
            responseCode = "409",
            description = "Email already exists"
        )
    })
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
