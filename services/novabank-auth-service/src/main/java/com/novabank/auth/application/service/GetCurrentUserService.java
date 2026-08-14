package com.novabank.auth.application.service;

import com.novabank.auth.application.port.security.CurrentUserProvider;
import com.novabank.auth.application.response.CurrentUserResponse;
import com.novabank.auth.application.security.JwtUser;
import com.novabank.auth.application.usecase.GetCurrentUserUseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class GetCurrentUserService implements GetCurrentUserUseCase {

    private final CurrentUserProvider currentUserProvider;

    @Override
    public CurrentUserResponse getCurrentUser() {

        JwtUser user = currentUserProvider.getCurrentUser();

        return new CurrentUserResponse(
            user.userId(),
            user.email(),
            user.roles()
        );
    }


}
