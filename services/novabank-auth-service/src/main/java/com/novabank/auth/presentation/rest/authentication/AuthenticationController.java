package com.novabank.auth.presentation.rest.authentication;

import com.novabank.auth.application.command.LoginUserCommand;
import com.novabank.auth.application.response.LoginUserResponse;
import com.novabank.auth.application.usecase.LoginUserUseCase;
import com.novabank.auth.presentation.rest.authentication.request.LoginRequest;
import com.novabank.auth.presentation.rest.authentication.response.LoginApiResponse;
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

@Tag(name = "Authentication")
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthenticationController {

    private final LoginUserUseCase loginUserUseCase;


    @PostMapping(
        value = "/login",
        consumes = MediaType.APPLICATION_JSON_VALUE,
        produces = MediaType.APPLICATION_JSON_VALUE
    )
    @ResponseStatus(HttpStatus.OK)
    @Operation(
        summary = "Authenticate user",
        description = "Authenticates a user using email and password."
    )
    @ApiResponses({
        @ApiResponse(
            responseCode = "200",
            description = "User authenticated successfully"
        ),
        @ApiResponse(
            responseCode = "400",
            description = "Validation failed"
        ),
        @ApiResponse(
            responseCode = "401",
            description = "Invalid email or password"
        )
    })
    public LoginApiResponse login(
        @Valid
        @RequestBody
        LoginRequest request) {

        LoginUserResponse response = loginUserUseCase.login(
            new LoginUserCommand(request.email(), request.password()));

        return new LoginApiResponse(
            response.userId(),
            response.email(),
            response.status());
    }
}
