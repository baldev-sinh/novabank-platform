package com.novabank.auth.application.port.security;

import com.novabank.auth.application.security.JwtUser;

public interface CurrentUserProvider {

    JwtUser getCurrentUser();

}
