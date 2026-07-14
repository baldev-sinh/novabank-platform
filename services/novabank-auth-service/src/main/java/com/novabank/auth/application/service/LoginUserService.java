package com.novabank.auth.application.service;

import com.novabank.auth.application.command.LoginUserCommand;
import com.novabank.auth.application.exception.InvalidCredentialsException;
import com.novabank.auth.application.port.security.PasswordEncoder;
import com.novabank.auth.application.response.LoginUserResponse;
import com.novabank.auth.application.usecase.LoginUserUseCase;
import com.novabank.auth.domain.model.User;
import com.novabank.auth.domain.repository.UserRepository;
import com.novabank.auth.domain.valueobject.EmailAddress;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class LoginUserService implements LoginUserUseCase {

    private final UserRepository repository;
    private final PasswordEncoder passwordEncoder;

    @Override
    public LoginUserResponse login(LoginUserCommand command) {
        Objects.requireNonNull(command, "command cannot be null");

        EmailAddress email = EmailAddress.of(command.email());

        User user = repository.findByEmail(email)
            .orElseThrow(
                () -> new InvalidCredentialsException("Invalid email or password.")
            );

        if (!passwordEncoder.matches(
            command.password(),
            user.passwordHash().value()
        )) {
            throw new InvalidCredentialsException(
                "Invalid email or password."
            );
        }

        return buildResponse(user);
    }

    private LoginUserResponse buildResponse(User user) {
        Objects.requireNonNull(user, "user cannot be null");
        return new LoginUserResponse(
            user.id().value(),
            user.email().value(),
            user.status().name()
        );
    }
}
