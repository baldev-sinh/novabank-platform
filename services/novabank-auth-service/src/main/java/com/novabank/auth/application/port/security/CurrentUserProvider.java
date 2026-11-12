package com.novabank.auth.application.port.security;

import com.novabank.auth.application.security.AuthenticatedUser;

public interface CurrentUserProvider {

  AuthenticatedUser getCurrentUser();
}
