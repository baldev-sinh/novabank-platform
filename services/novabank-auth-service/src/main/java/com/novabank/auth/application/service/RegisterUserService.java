package com.novabank.auth.application.service;

import com.novabank.auth.application.command.RegisterUserCommand;
import com.novabank.auth.application.exception.DuplicateEmailException;
import com.novabank.auth.application.response.RegisterUserResponse;
import com.novabank.auth.application.usecase.RegisterUserUseCase;
import com.novabank.auth.domain.model.User;
import com.novabank.auth.domain.repository.UserRepository;
import com.novabank.auth.domain.valueobject.EmailAddress;
import com.novabank.auth.domain.valueobject.PasswordHash;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class RegisterUserService implements RegisterUserUseCase {

    private final UserRepository repository;

    @Override
    public RegisterUserResponse register(RegisterUserCommand command) {

        // null check for request
        Objects.requireNonNull(command, "command cannot be null");

        // check duplicate email
        EmailAddress email = EmailAddress.of(command.email());

        if(repository.existsByEmail(email)){
            throw new DuplicateEmailException("Email already registered: " + email);
        }

        // get password hash
        PasswordHash passwordHash = PasswordHash.of(command.password());

        // register user using User domain
        User user = User.register(email, passwordHash);

        // save user
        User savedUser = repository.save(user);

        // prepare response
        return buildResponse(savedUser);
    }

    private RegisterUserResponse buildResponse(User user) {
        return new RegisterUserResponse(
            user.id().value(),
            user.email().value(),
            user.status()
        );
    }
}
