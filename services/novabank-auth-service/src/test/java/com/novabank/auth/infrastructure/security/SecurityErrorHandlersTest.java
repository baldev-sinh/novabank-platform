package com.novabank.auth.infrastructure.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.json.ProblemDetailJacksonMixin;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import tools.jackson.databind.json.JsonMapper;

class SecurityErrorHandlersTest {

  // Same ProblemDetail mix-in that Spring Boot registers on its JsonMapper.
  private final JsonMapper jsonMapper =
      JsonMapper.builder().addMixIn(ProblemDetail.class, ProblemDetailJacksonMixin.class).build();

  @Test
  @DisplayName("Entry point returns 401 Problem Details with a Bearer challenge")
  void entryPointReturns401() throws Exception {
    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/auth/me");
    request.setQueryString("token=secret-in-query");
    MockHttpServletResponse response = new MockHttpServletResponse();

    new JwtAuthenticationEntryPoint(jsonMapper)
        .commence(request, response, new BadCredentialsException("token expired at 10:00"));

    assertThat(response.getStatus()).isEqualTo(401);
    assertThat(response.getHeader(HttpHeaders.WWW_AUTHENTICATE)).isEqualTo("Bearer");
    assertThat(response.getContentType()).isEqualTo(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
    assertThat(response.getContentAsString())
        .contains(
            "\"status\":401", "\"title\":\"Unauthorized\"", "\"instance\":\"/api/v1/auth/me\"")
        .doesNotContain("token expired", "secret-in-query");
  }

  @Test
  @DisplayName("Access denied handler returns 403 Problem Details without a challenge")
  void accessDeniedHandlerReturns403() throws Exception {
    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/admin/users");
    MockHttpServletResponse response = new MockHttpServletResponse();

    new JwtAccessDeniedHandler(jsonMapper)
        .handle(request, response, new AccessDeniedException("requires ROLE_ADMIN"));

    assertThat(response.getStatus()).isEqualTo(403);
    assertThat(response.getHeader(HttpHeaders.WWW_AUTHENTICATE)).isNull();
    assertThat(response.getContentType()).isEqualTo(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
    assertThat(response.getContentAsString())
        .contains("\"status\":403", "\"title\":\"Forbidden\"")
        .doesNotContain("ROLE_ADMIN");
  }
}
