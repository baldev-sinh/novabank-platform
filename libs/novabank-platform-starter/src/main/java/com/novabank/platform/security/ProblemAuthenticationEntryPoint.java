package com.novabank.platform.security;

import com.novabank.platform.web.ProblemTraceId;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URI;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import tools.jackson.databind.json.JsonMapper;

/**
 * Returns a 401 Problem Details response (RFC 9457) when a request has no valid credentials.
 *
 * <p>Logged-in users without the required permission are handled by {@link
 * ProblemAccessDeniedHandler} (403) instead.
 */
public class ProblemAuthenticationEntryPoint implements AuthenticationEntryPoint {

  private final JsonMapper jsonMapper;

  public ProblemAuthenticationEntryPoint(JsonMapper jsonMapper) {
    this.jsonMapper = jsonMapper;
  }

  @Override
  public void commence(
      HttpServletRequest request,
      HttpServletResponse response,
      AuthenticationException authException)
      throws IOException {

    // Keep the message generic: the exception text can reveal why the token was rejected.
    ProblemDetail problem =
        ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, "Authentication is required.");
    problem.setInstance(URI.create(request.getRequestURI()));
    ProblemTraceId.addTo(problem);

    response.setStatus(HttpStatus.UNAUTHORIZED.value());
    // HTTP requires a 401 to say which auth scheme the client should use (RFC 9110).
    response.setHeader(HttpHeaders.WWW_AUTHENTICATE, "Bearer");
    response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);

    jsonMapper.writeValue(response.getOutputStream(), problem);
  }
}
