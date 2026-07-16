package com.novabank.auth.application.port.security;

import com.novabank.auth.application.security.JwtUser;


public interface TokenService {

    String generateAccessToken(JwtUser user);

    boolean validate(String token);

    JwtUser parse(String token);

    long accessTokenExpiration();

}
