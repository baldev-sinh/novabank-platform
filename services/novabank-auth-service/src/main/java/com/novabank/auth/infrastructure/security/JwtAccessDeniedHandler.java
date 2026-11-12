package com.novabank.auth.infrastructure.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URI;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/**
 * Returns a 403 Problem Details response (RFC 9457) when a logged-in user tries to access something
 * they don't have permission for.
 *
 * <p>Unauthenticated requests are handled by {@link JwtAuthenticationEntryPoint} (401) instead.
 */
@Component
@RequiredArgsConstructor
public class JwtAccessDeniedHandler implements AccessDeniedHandler {

  private final JsonMapper jsonMapper;

  @Override
  public void handle(
      HttpServletRequest request,
      HttpServletResponse response,
      AccessDeniedException accessDeniedException)
      throws IOException {

    // Keep the message generic: the exception text can reveal which role or rule blocked the user.
    ProblemDetail problem =
        ProblemDetail.forStatusAndDetail(
            HttpStatus.FORBIDDEN, "You do not have permission to access this resource.");
    problem.setInstance(URI.create(request.getRequestURI()));

    response.setStatus(HttpStatus.FORBIDDEN.value());
    response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);

    jsonMapper.writeValue(response.getOutputStream(), problem);
  }
}
